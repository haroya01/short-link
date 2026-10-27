package com.example.short_link.common.observability;

import java.time.Instant;
import java.util.List;

public interface RequestMetricReader {

  List<RequestMetric> between(Instant from, Instant to);

  List<RequestMetric> forShortCodeBetween(String shortCode, Instant from, Instant to);
}
