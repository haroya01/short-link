package com.example.short_link.federation.application.directory;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class DirectoryPacer {

  public void pause(Duration gap) {
    if (gap.isZero() || gap.isNegative()) {
      return;
    }
    try {
      Thread.sleep(gap);
    } catch (InterruptedException stopping) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("directory run stopped", stopping);
    }
  }
}
