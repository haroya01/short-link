package com.example.short_link.note.scheduler;

import com.example.short_link.note.application.write.NoteLinkPreviewRefill;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoteLinkPreviewRefillJob {

  private final NoteLinkPreviewRefill refill;

  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1S")
  public void step() {
    refill.step();
  }
}
