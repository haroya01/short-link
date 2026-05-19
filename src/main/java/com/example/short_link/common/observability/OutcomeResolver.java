package com.example.short_link.common.observability;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Classifies a finished request into a coarse {@code outcome} that's friendlier than a bare HTTP
 * status for ops dashboards. The mapping is intentionally simple — the controller can override by
 * setting the {@link #ATTRIBUTE} request attribute (e.g. RedirectController stamps {@code expired}
 * / {@code blocked} for the 410/451 paths) so we get a domain-aware label without coupling this
 * resolver to every controller's semantics.
 */
public final class OutcomeResolver {

  /** Request attribute the controller can set to override the default status-based mapping. */
  public static final String ATTRIBUTE = "kurl.request.outcome";

  private OutcomeResolver() {}

  public static String resolve(HttpServletRequest request, int status) {
    Object override = request.getAttribute(ATTRIBUTE);
    if (override instanceof String s && !s.isBlank()) return s;
    return fromStatus(status);
  }

  static String fromStatus(int status) {
    if (status >= 200 && status < 300) return "ok";
    if (status == 301 || status == 302 || status == 307 || status == 308) return "redirect";
    if (status == 304) return "not_modified";
    if (status == 401) return "unauthorized";
    if (status == 403) return "forbidden";
    if (status == 404) return "not_found";
    if (status == 410) return "expired";
    if (status == 429) return "rate_limited";
    if (status == 451) return "blocked";
    if (status >= 500) return "error";
    if (status >= 400) return "client_error";
    return "other";
  }
}
