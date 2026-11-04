package com.example.short_link.link.og.application;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class OgPageFetcher {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
  private static final int MAX_BODY_BYTES = 1_024 * 1_024;
  private static final int MAX_REDIRECTS = 3;
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final Map<String, String> HEADERS =
      Map.of(
          "User-Agent", "kurl-link-preview/1.0 (+https://kurl.me/bot)",
          "Accept", "text/html,application/xhtml+xml",
          "Accept-Language", "en;q=0.9,ko;q=0.8,ja;q=0.7");

  private final HttpFetcher httpFetcher;

  OgPageFetcher(HttpFetcher httpFetcher) {
    this.httpFetcher = httpFetcher;
  }

  Landing fetch(Resolved first) {
    Resolved current = first;
    Set<URI> visited = new HashSet<>();
    for (int hop = 0; ; hop++) {
      visited.add(current.uri());
      Response response =
          httpFetcher.fetch(
              Request.getNoRedirects(
                  current, HEADERS, CONNECT_TIMEOUT, READ_TIMEOUT, MAX_BODY_BYTES));
      if (!REDIRECTS.contains(response.status())) {
        return new Landing(current.uri(), response, null);
      }
      if (hop == MAX_REDIRECTS) {
        return Landing.refused("too_many_redirects");
      }
      URI next = location(current.uri(), response.header("Location"));
      if (next == null) {
        return Landing.refused("redirect_invalid");
      }
      if (visited.contains(next)) {
        return Landing.refused("redirect_loop");
      }
      Optional<Resolved> target = PublicHttpUrlGuard.resolve(next.toString());
      if (target.isEmpty()) {
        return Landing.refused("redirect_blocked");
      }
      current = target.get();
    }
  }

  private static URI location(URI from, String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      return from.resolve(header.strip());
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  record Landing(URI uri, Response response, String refusal) {

    static Landing refused(String refusal) {
      return new Landing(null, null, refusal);
    }
  }
}
