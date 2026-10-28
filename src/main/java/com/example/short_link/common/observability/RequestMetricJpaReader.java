package com.example.short_link.common.observability;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class RequestMetricJpaReader implements RequestMetricReader {

  private final RequestMetricJpaRepository repository;

  RequestMetricJpaReader(RequestMetricJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<RequestMetric> between(Instant from, Instant to) {
    return repository.findWindow(from, to).stream().map(RequestMetricEntity::toMetric).toList();
  }

  @Override
  public List<RequestMetric> forShortCodeBetween(String shortCode, Instant from, Instant to) {
    return repository.findShortCodeWindow(shortCode, from, to).stream()
        .map(RequestMetricEntity::toMetric)
        .toList();
  }
}
