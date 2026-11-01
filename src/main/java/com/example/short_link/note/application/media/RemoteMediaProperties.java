package com.example.short_link.note.application.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.remote-media")
public record RemoteMediaProperties(
    boolean enabled, int queue, int maxBytes, Duration keepFor, int sweepBatch) {

  public RemoteMediaProperties {
    queue = queue <= 0 ? 100 : queue;
    maxBytes = maxBytes <= 0 ? 10 * 1024 * 1024 : maxBytes;
    keepFor = keepFor == null ? Duration.ofDays(30) : keepFor;
    sweepBatch = sweepBatch <= 0 ? 200 : sweepBatch;
  }
}
