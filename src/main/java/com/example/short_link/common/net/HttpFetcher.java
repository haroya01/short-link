package com.example.short_link.common.net;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Each request connects only to its pre-resolved IPs to prevent DNS rebinding, then closes its
 * client. Returns the complete body, capped at {@link Request#maxBodyBytes}.
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
      int maxBodyBytes,
      boolean followRedirects) {

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
          Method.GET, pinned, headers, null, null, connectTimeout, readTimeout, maxBodyBytes, true);
    }

    /**
     * Returns 3xx without following them. The pinned resolver rejects other hosts, so callers must
     * follow cross-host redirects hop by hop and validate each Location with {@link
     * PublicHttpUrlGuard}.
     */
    public static Request getNoRedirects(
        Resolved pinned,
        Map<String, String> headers,
        Duration connectTimeout,
        Duration readTimeout,
        int maxBodyBytes) {
      return new Request(
          Method.GET,
          pinned,
          headers,
          null,
          null,
          connectTimeout,
          readTimeout,
          maxBodyBytes,
          false);
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
          maxBodyBytes,
          true);
    }

    public URI uri() {
      return pinned.uri();
    }
  }

  /** An absent body becomes an empty array, never null. Header lookups are case-insensitive. */
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
