package com.example.short_link.federation.application.directory;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.federation.directory")
public record DirectoryProperties(
    boolean enabled,
    int newPerRun,
    int defaultCap,
    Duration consent,
    Duration pageGap,
    Duration fetchGap,
    Duration pagePatience) {

  public DirectoryProperties {
    newPerRun = newPerRun <= 0 ? 100 : newPerRun;
    defaultCap = defaultCap <= 0 ? 1_000 : defaultCap;
    consent = consent == null ? Duration.ofDays(3) : consent;
    pageGap = pageGap == null ? Duration.ofSeconds(12) : pageGap;
    fetchGap = fetchGap == null ? Duration.ofSeconds(3) : fetchGap;
    pagePatience = pagePatience == null ? Duration.ofSeconds(20) : pagePatience;
  }
}
