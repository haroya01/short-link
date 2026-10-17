package com.example.short_link.federation.application.relay;

import com.example.short_link.federation.domain.RelayMode;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.relay")
public record RelayProperties(
    boolean enabled,
    int perMinute,
    int perDay,
    int queue,
    Duration patience,
    Duration slowAfter,
    RelayMode mode,
    List<String> subscriptions) {

  public RelayProperties {
    perMinute = perMinute <= 0 ? 30 : perMinute;
    perDay = perDay <= 0 ? 2_000 : perDay;
    queue = queue <= 0 ? 500 : queue;
    patience = patience == null ? Duration.ofSeconds(5) : patience;
    slowAfter = slowAfter == null ? Duration.ofSeconds(4) : slowAfter;
    subscriptions =
        subscriptions == null
            ? List.of()
            : subscriptions.stream().map(String::strip).filter(uri -> !uri.isEmpty()).toList();
  }
}
