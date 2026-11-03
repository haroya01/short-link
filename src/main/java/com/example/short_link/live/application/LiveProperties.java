package com.example.short_link.live.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.live")
public record LiveProperties(
    Integer maxStreams,
    int maxStreamsPerUser,
    Duration lifetime,
    Duration heartbeat,
    Duration window,
    Duration windowPerStream,
    Duration maxWindow) {

  public LiveProperties {
    maxStreams = maxStreams == null ? 500 : Math.max(0, maxStreams);
    maxStreamsPerUser = maxStreamsPerUser <= 0 ? 4 : maxStreamsPerUser;
    lifetime = lifetime == null ? Duration.ofMinutes(5) : lifetime;
    heartbeat = heartbeat == null ? Duration.ofSeconds(25) : heartbeat;
    window = window == null ? Duration.ofMillis(300) : window;
    windowPerStream = windowPerStream == null ? Duration.ofMillis(20) : windowPerStream;
    maxWindow = maxWindow == null ? Duration.ofSeconds(10) : maxWindow;
  }

  Duration windowFor(int openStreams) {
    Duration scaled = windowPerStream.multipliedBy(openStreams);
    if (scaled.compareTo(window) < 0) {
      return window;
    }
    return scaled.compareTo(maxWindow) > 0 ? maxWindow : scaled;
  }
}
