package com.example.short_link.federation.application.trends;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.latest")
public record LatestProperties(
    boolean enabled,
    List<String> hosts,
    int perHost,
    int maxItems,
    Duration freshFor,
    Duration patience) {

  static final int MAX_PER_HOST = 40;

  public LatestProperties {
    hosts =
        hosts == null
            ? List.of()
            : hosts.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .distinct()
                .toList();
    perHost = perHost <= 0 ? MAX_PER_HOST : Math.min(perHost, MAX_PER_HOST);
    maxItems = maxItems <= 0 ? 600 : maxItems;
    freshFor = freshFor == null ? Duration.ofMinutes(5) : freshFor;
    patience = patience == null ? Duration.ofSeconds(3) : patience;
  }
}
