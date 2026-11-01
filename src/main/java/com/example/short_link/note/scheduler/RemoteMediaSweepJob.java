package com.example.short_link.note.scheduler;

import com.example.short_link.note.application.media.RemoteMediaQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemoteMediaSweepJob {

  private final RemoteMediaQueue queue;

  @Scheduled(
      fixedDelayString = "${short-link.remote-media.sweep-delay:PT10M}",
      initialDelayString = "${short-link.remote-media.sweep-delay:PT10M}")
  public void sweep() {
    queue.sweep();
  }
}
