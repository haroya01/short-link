package com.example.short_link.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single source of truth for "what IP is this request from". Always returns {@code
 * req.getRemoteAddr()} — never reads the {@code X-Forwarded-For} header directly. Tomcat's
 * RemoteIpValve (enabled in prod via {@code server.forward-headers-strategy: native}) rewrites
 * {@code remoteAddr} to the real client IP only when the request actually arrived from a trusted
 * internal proxy. A direct {@code XFF} read instead lets an arbitrary external caller forge the
 * value and bypass IP-keyed rate limits, bot heuristics, and audit fields.
 */
public final class ClientIp {

  private ClientIp() {}

  public static String of(HttpServletRequest req) {
    return req.getRemoteAddr();
  }
}
