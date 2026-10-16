package com.example.short_link.federation.application.trends;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.trends")
public record TrendsProperties(
    boolean enabled, List<String> hosts, int perHost, Duration every, Duration patience) {

  // Mastodon answers at most 40 trending posts a request.
  static final int MAX_PER_HOST = 40;

  public TrendsProperties {
    hosts =
        hosts == null
            ? List.of()
            : hosts.stream()
                .map(host -> host.strip().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .distinct()
                .toList();
    perHost = perHost <= 0 ? 20 : Math.min(perHost, MAX_PER_HOST);
    every = every == null ? Duration.ofMinutes(30) : every;
    patience = patience == null ? Duration.ofSeconds(5) : patience;
  }
}
