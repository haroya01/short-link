package com.example.short_link.blogimport.application;

import com.example.short_link.blogimport.application.BlogImportProperties.RobotsPolicy;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RobotsGate {

  public static final String AGENT_TOKEN = "kurl-importer";
  // Shorter than ImportSourceEntity.FETCH_INTERVAL: every sync reads robots.txt again.
  static final Duration CACHE_FOR = Duration.ofMinutes(25);

  private record Cached(RobotsRules rules, Instant until) {}

  private final BlogFeedFetcher fetcher;
  private final RobotsPolicy policy;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public RobotsGate(BlogFeedFetcher fetcher, BlogImportProperties properties, Clock clock) {
    this.fetcher = fetcher;
    this.policy = properties.robotsPolicy();
    this.clock = clock;
  }

  public void requireAllowed(String url) {
    if (policy == RobotsPolicy.IGNORE) {
      return;
    }
    URI uri = URI.create(url);
    String origin =
        uri.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + uri.getRawAuthority().toLowerCase(Locale.ROOT);
    String path =
        (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    if (!rules(origin).allows(AGENT_TOKEN, path, policy == RobotsPolicy.NAMED_ONLY)) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_ROBOTS_DISALLOWED, url);
    }
  }

  private RobotsRules rules(String origin) {
    Instant now = clock.instant();
    Cached cached = cache.get(origin);
    if (cached != null && cached.until().isAfter(now)) {
      return cached.rules();
    }
    RobotsRules rules =
        fetcher.robots(origin).map(RobotsRules::parse).orElse(RobotsRules.parse(""));
    cache.put(origin, new Cached(rules, now.plus(CACHE_FOR)));
    return rules;
  }
}
