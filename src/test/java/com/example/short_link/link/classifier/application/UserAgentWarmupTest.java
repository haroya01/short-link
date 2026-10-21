package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.UserAgentInfo;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class UserAgentWarmupTest {

  private final UserAgentClassifier classifier = mock(UserAgentClassifier.class);
  private final UserAgentWarmup warmup = new UserAgentWarmup(classifier);

  @Test
  void startingReturnsWhileTheAnalyzerIsStillWarming() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    when(classifier.classify(anyString()))
        .thenAnswer(
            call -> {
              release.await(5, TimeUnit.SECONDS);
              return UserAgentInfo.unknown();
            });

    long started = System.nanoTime();
    warmup.started();
    long returnedAfter = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    release.countDown();

    assertThat(returnedAfter).isLessThan(1_000);
    for (String agent : UserAgentWarmup.AGENTS) {
      verify(classifier, timeout(5_000)).classify(agent);
    }
  }

  @Test
  void aFailedWarmUpIsLoggedAndSwallowed(CapturedOutput output) {
    when(classifier.classify(anyString())).thenThrow(new IllegalStateException("no matchers"));

    assertThatCode(warmup::warm).doesNotThrowAnyException();
    assertThat(output).contains("user agent analyzer warm-up failed");
    assertThat(output).contains("no matchers");
  }
}
