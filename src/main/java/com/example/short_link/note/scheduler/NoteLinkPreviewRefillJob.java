package com.example.short_link.note.scheduler;

import com.example.short_link.note.application.write.NoteLinkPreviewRefill;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Component;

@Component
public class NoteLinkPreviewRefillJob implements DisposableBean {

  private final NoteLinkPreviewRefill refill;
  private final ThreadPoolExecutor worker;

  public NoteLinkPreviewRefillJob(NoteLinkPreviewRefill refill) {
    this.refill = refill;
    CustomizableThreadFactory threads = new CustomizableThreadFactory("note-link-refill-");
    threads.setDaemon(true);
    this.worker =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            threads,
            new ThreadPoolExecutor.DiscardPolicy());
  }

  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1S")
  public void step() {
    worker.execute(refill::step);
  }

  @Override
  public void destroy() throws InterruptedException {
    worker.shutdownNow();
    worker.awaitTermination(5, TimeUnit.SECONDS);
  }
}
