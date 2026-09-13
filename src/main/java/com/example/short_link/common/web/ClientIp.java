package com.example.short_link.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Use remoteAddr after Tomcat's trusted-proxy RemoteIpValve processing. Reading X-Forwarded-For
 * directly lets callers forge IPs used by rate limits, bot detection, and auditing.
 */
public final class ClientIp {

  private ClientIp() {}

  public static String of(HttpServletRequest req) {
    return req.getRemoteAddr();
  }
}
