package com.example.short_link.note.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.short_link.note.application.write.NoteLinkPreviewRefill;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class NoteLinkPreviewRefillJobTest {

  @Test
  void eachTickIsOneStepAtLeastASecondAfterTheLastEnded() throws NoSuchMethodException {
    NoteLinkPreviewRefill refill = mock(NoteLinkPreviewRefill.class);

    new NoteLinkPreviewRefillJob(refill).step();

    verify(refill).step();
    Scheduled tick =
        NoteLinkPreviewRefillJob.class.getMethod("step").getAnnotation(Scheduled.class);
    assertThat(Duration.parse(tick.fixedDelayString()))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
    assertThat(tick.fixedRateString()).isEmpty();
  }
}
