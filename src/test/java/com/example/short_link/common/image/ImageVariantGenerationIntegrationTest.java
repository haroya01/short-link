package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@SpringBootTest(
    properties = {
      "short-link.image-variants.enabled=true",
      "short-link.avatar.bucket=variants-it",
      "short-link.avatar.region=ap-northeast-1"
    })
@ActiveProfiles("test")
class ImageVariantGenerationIntegrationTest {

  @MockitoBean private S3Client s3;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;

  private final String key = "note-images/990001/" + UUID.randomUUID() + ".jpg";

  @AfterEach
  void forget() {
    jdbc.update("DELETE FROM image_variant WHERE storage_key = ?", key);
  }

  @Test
  void anAcceptedOriginalGetsBothCopiesWrittenWithItsHeadersAfterTheRequestReturns()
      throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    byte[] photo = ImageFixtures.photo(1800, 2400);
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenAnswer(
            call -> {
              release.await(10, TimeUnit.SECONDS);
              return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), photo);
            });

    long started = System.nanoTime();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction -> {
              events.publishEvent(ImageVariantsRequested.of(key));
              sleep(Duration.ofMillis(200));
              verify(s3, never()).getObjectAsBytes(any(GetObjectRequest.class));
            });
    long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

    verify(s3, timeout(5000)).getObjectAsBytes(any(GetObjectRequest.class));
    assertThat(tookMs).isLessThan(1000);
    verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));

    release.countDown();
    ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3, timeout(10_000).times(2)).putObject(puts.capture(), any(RequestBody.class));
    String stem = key.substring(0, key.length() - ".jpg".length());
    assertThat(puts.getAllValues())
        .extracting(PutObjectRequest::key)
        .containsExactly(stem + ".w1080.jpg", stem + ".w480.jpg");
    assertThat(puts.getAllValues())
        .allSatisfy(
            put -> {
              assertThat(put.bucket()).isEqualTo("variants-it");
              assertThat(put.contentType()).isEqualTo("image/jpeg");
              assertThat(put.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
            });
    assertThat(outcome()).isEqualTo("MADE");

    events.publishEvent(ImageVariantsRequested.of(key));
    sleep(Duration.ofMillis(300));
    verify(s3, times(1)).getObjectAsBytes(any(GetObjectRequest.class));
  }

  private String outcome() {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      List<String> rows =
          jdbc.queryForList(
              "SELECT outcome FROM image_variant WHERE storage_key = ?", String.class, key);
      if (!rows.isEmpty()) {
        return rows.getFirst();
      }
      sleep(Duration.ofMillis(20));
    }
    return null;
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
