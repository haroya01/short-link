package com.example.short_link.common.net;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Outbound HTTP port. Wraps the Apache HttpClient 5 + DNS-pinning machinery
 * (PinnedHttpClientFactory) so callers don't depend on {@code org.apache.hc.*} directly — that
 * dependency stays confined to the adapter in {@code common.net}.
 *
 * <p>Each call is independent: the implementation builds a per-request client pinned to the
 * resolved IP (DNS rebinding defense) and closes it after the response is fully read. Body is
 * capped at {@link Request#maxBodyBytes} and returned as a complete {@code byte[]} — streaming
 * responses are out of scope, which fits the OG-scrape / webhook-delivery callers we have today.
 */
public interface HttpFetcher {

  Response fetch(Request request);

  enum Method {
    GET,
    POST
  }

  record Request(
      Method method,
      Resolved pinned,
      Map<String, String> headers,
      byte[] body,
      String bodyContentType,
      Duration connectTimeout,
      Duration readTimeout,
      int maxBodyBytes) {

    public Request {
      if (method == null) throw new IllegalArgumentException("method required");
      if (pinned == null) throw new IllegalArgumentException("pinned required");
      headers = headers == null ? Map.of() : Map.copyOf(headers);
      if (method == Method.POST && body == null) {
        throw new IllegalArgumentException("body required for POST");
      }
    }

    public static Request get(
        Resolved pinned,
        Map<String, String> headers,
        Duration connectTimeout,
        Duration readTimeout,
        int maxBodyBytes) {
      return new Request(
          Method.GET, pinned, headers, null, null, connectTimeout, readTimeout, maxBodyBytes);
    }

    public static Request post(
        Resolved pinned,
        Map<String, String> headers,
        byte[] body,
        String bodyContentType,
        Duration connectTimeout,
        Duration readTimeout,
        int maxBodyBytes) {
      return new Request(
          Method.POST,
          pinned,
          headers,
          body,
          bodyContentType,
          connectTimeout,
          readTimeout,
          maxBodyBytes);
    }

    public URI uri() {
      return pinned.uri();
    }
  }

  /**
   * Response value. {@code body} is empty (not null) when the entity was absent. Header lookups are
   * case-insensitive on read.
   */
  record Response(int status, Map<String, List<String>> headers, byte[] body) {

    public Response {
      headers = headers == null ? Map.of() : Map.copyOf(headers);
      body = body == null ? new byte[0] : body;
    }

    /** First value for the given header (case-insensitive), or null. */
    public String header(String name) {
      for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
          return entry.getValue().get(0);
        }
      }
      return null;
    }
  }
}
