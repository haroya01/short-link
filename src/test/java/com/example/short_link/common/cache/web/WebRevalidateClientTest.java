package com.example.short_link.common.cache.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class WebRevalidateClientTest {

  private static final String URL = "https://blog.kurl.me/revalidate";
  private static final String SECRET = "s3cret-value";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer web = MockRestServiceServer.bindTo(builder).build();
  private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

  private WebRevalidateClient client(String secret) {
    return new WebRevalidateClient(
        builder.build(), new WebRevalidateProperties(URL, secret), scheduler);
  }

  private Runnable scheduledSend() {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(task.capture(), eq(2000L), eq(TimeUnit.MILLISECONDS));
    return task.getValue();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void withoutASecretNothingIsScheduledOrSent(String secret) {
    WebRevalidateClient client = client(secret);

    client.requestRevalidation();
    client.requestRevalidation();

    verifyNoInteractions(scheduler);
    web.verify();
  }

  @Test
  void requestsInsideTheWindowBecomeOneCallAndTheNextRequestOpensANewWindow() {
    web.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withNoContent());
    WebRevalidateClient client = client(SECRET);

    client.requestRevalidation();
    client.requestRevalidation();
    client.requestRevalidation();
    Runnable send = scheduledSend();
    send.run();
    client.requestRevalidation();
    client.requestRevalidation();

    verify(scheduler, times(2)).schedule(any(Runnable.class), eq(2000L), eq(TimeUnit.MILLISECONDS));
    send.run();
    web.verify();
  }

  @Test
  void theCallCarriesTheSecretHeaderAndAnEmptyJsonObject() {
    web.expect(ExpectedCount.once(), requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Revalidate-Secret", SECRET))
        .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().string("{}"))
        .andRespond(withNoContent());
    WebRevalidateClient client = client(SECRET);

    client.requestRevalidation();
    scheduledSend().run();

    web.verify();
  }

  static Stream<Named<ResponseCreator>> failures() {
    return Stream.of(
        named("401 wrong secret", withStatus(HttpStatus.UNAUTHORIZED)),
        named("429 too many", withTooManyRequests()),
        named("500", withServerError()),
        named("read timeout", withException(new SocketTimeoutException("read timed out"))));
  }

  @ParameterizedTest
  @MethodSource("failures")
  void aFailedCallStaysInsideAsOneWarningWithoutTheSecret(
      ResponseCreator failure, CapturedOutput output) {
    web.expect(ExpectedCount.once(), requestTo(URL)).andRespond(failure);
    WebRevalidateClient client = client(SECRET);
    client.requestRevalidation();
    Runnable send = scheduledSend();

    assertThatCode(send::run).doesNotThrowAnyException();

    web.verify();
    assertThat(output.getOut()).contains("web cache revalidation failed").doesNotContain(SECRET);
  }

  @Test
  void aFailedCallStillLetsTheNextRequestThrough() {
    web.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withServerError());
    WebRevalidateClient client = client(SECRET);

    client.requestRevalidation();
    scheduledSend().run();
    client.requestRevalidation();

    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(2)).schedule(task.capture(), anyLong(), any(TimeUnit.class));
    task.getValue().run();
    web.verify();
  }

  @Test
  void aRequestAfterShutdownIsDroppedQuietlyAndReopensTheWindow() {
    when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenThrow(new RejectedExecutionException("shut down"))
        .thenReturn(null);
    WebRevalidateClient client = client(SECRET);

    assertThatCode(client::requestRevalidation).doesNotThrowAnyException();
    client.requestRevalidation();

    verify(scheduler, times(2)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  @Test
  void theRealSchedulerSendsOnceAboutTwoSecondsAfterTheFirstRequestAndStopsOnDestroy() {
    AtomicReference<Thread> sender = new AtomicReference<>();
    web.expect(ExpectedCount.once(), requestTo(URL))
        .andRespond(
            request -> {
              sender.set(Thread.currentThread());
              return withNoContent().createResponse(request);
            });
    WebRevalidateClient client =
        new WebRevalidateClient(builder.build(), new WebRevalidateProperties(URL, SECRET));
    long started = System.nanoTime();

    for (int i = 0; i < 5; i++) {
      client.requestRevalidation();
    }

    web.verify(Duration.ofSeconds(5));
    assertThat(Duration.ofNanos(System.nanoTime() - started))
        .isGreaterThanOrEqualTo(Duration.ofMillis(1900));
    assertThat(sender.get().getName()).isEqualTo("kurl-web-revalidate");
    assertThat(sender.get().isDaemon()).isTrue();
    client.destroy();
    assertThatCode(client::requestRevalidation).doesNotThrowAnyException();
  }
}
