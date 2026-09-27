package com.example.short_link.link.health.application;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.health.domain.DestinationFailure;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DestinationProbe {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
  private static final int MAX_BODY_BYTES = 1024;
  private static final int MAX_HOPS = 5;
  private static final Map<String, String> HEADERS =
      Map.of(
          "User-Agent", "kurl-link-check/1.0 (+https://kurl.me/bot)",
          "Accept", "text/html,application/xhtml+xml,*/*;q=0.8");

  private final HttpFetcher httpFetcher;

  public DestinationCheck check(String url) {
    Optional<Resolved> resolved = PublicHttpUrlGuard.resolve(url);
    if (resolved.isEmpty()) {
      return unresolved(url);
    }
    Resolved current = resolved.get();
    for (int hop = 0; hop <= MAX_HOPS; hop++) {
      Response response;
      try {
        response =
            httpFetcher.fetch(
                Request.getNoRedirects(
                    current, HEADERS, CONNECT_TIMEOUT, READ_TIMEOUT, MAX_BODY_BYTES));
      } catch (RuntimeException e) {
        return DestinationCheck.inconclusive();
      }
      int status = response.status();
      if (!isRedirect(status)) {
        return classify(status);
      }
      String location = response.header("Location");
      if (location == null || location.isBlank()) {
        return DestinationCheck.inconclusive();
      }
      String next;
      try {
        next = current.uri().resolve(location.trim()).toString();
      } catch (IllegalArgumentException e) {
        return DestinationCheck.inconclusive();
      }
      Optional<Resolved> hopResolved = PublicHttpUrlGuard.resolve(next);
      if (hopResolved.isEmpty()) {
        return unresolved(next);
      }
      current = hopResolved.get();
    }
    return DestinationCheck.inconclusive();
  }

  private static DestinationCheck unresolved(String url) {
    return PublicHttpUrlGuard.hostExists(url)
        ? DestinationCheck.inconclusive()
        : DestinationCheck.broken(DestinationFailure.NO_HOST, null);
  }

  private static DestinationCheck classify(int status) {
    if (status == 404) return DestinationCheck.broken(DestinationFailure.NOT_FOUND, status);
    if (status == 410) return DestinationCheck.broken(DestinationFailure.GONE, status);
    if (status >= 200 && status < 300) return DestinationCheck.healthy(status);
    return DestinationCheck.inconclusive();
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
  }
}
