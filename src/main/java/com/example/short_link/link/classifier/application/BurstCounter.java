package com.example.short_link.link.classifier.application;

import java.time.Duration;

public interface BurstCounter {
  Long increment(String key, Duration ttl);
}
