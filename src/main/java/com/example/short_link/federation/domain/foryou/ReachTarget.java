package com.example.short_link.federation.domain.foryou;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Mastodon names a local status https://{host}/users/{name}/statuses/{id}, and its API serves it
// at /api/v1/statuses/{id} on that host.
public record ReachTarget(String host, String id) {

  private static final Pattern MASTODON =
      Pattern.compile(
          "https://([a-z0-9.-]{1,253})/users/[A-Za-z0-9_.-]{1,128}/statuses/(\\d{1,30})");

  public static Optional<ReachTarget> of(String uri) {
    if (uri == null) {
      return Optional.empty();
    }
    Matcher matcher = MASTODON.matcher(uri);
    return matcher.matches()
        ? Optional.of(new ReachTarget(matcher.group(1), matcher.group(2)))
        : Optional.empty();
  }
}
