package com.example.short_link.common.net;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

public final class Redirects {

  private static final Set<Integer> STATUSES = Set.of(301, 302, 303, 307, 308);

  private Redirects() {}

  public static boolean isRedirect(int status) {
    return STATUSES.contains(status);
  }

  public static Optional<URI> next(URI from, String location) {
    if (location == null || location.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(from.resolve(location.strip()));
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }
}
