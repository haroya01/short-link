package com.example.short_link.common.observability;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controllers can override status-based outcomes through {@link #ATTRIBUTE} without adding
 * domain-specific rules here.
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
