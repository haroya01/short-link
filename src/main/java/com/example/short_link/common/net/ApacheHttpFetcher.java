package com.example.short_link.common.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

/**
 * Apache HttpClient 5 implementation of {@link HttpFetcher}. The single place in the codebase that
 * touches {@code org.apache.hc.*} — see ArchUnit {@code apacheHttpClientConfinedExceptKnownLeaks}.
 *
 * <p>Builds a per-request pinned client (DNS rebinding defense via {@link
 * PinnedHttpClientFactory}), executes the request, reads the response body up to {@code
 * maxBodyBytes}, and closes the client. Connection reuse across calls is intentionally not done —
 * pinning the resolved IP per request matters more than connection pooling for our outbound traffic
 * profile (low QPS, security-critical).
 */
@Component
public class ApacheHttpFetcher implements HttpFetcher {

  @Override
  public Response fetch(Request request) {
    Timeout connect = Timeout.ofMilliseconds(request.connectTimeout().toMillis());
    Timeout read = Timeout.ofMilliseconds(request.readTimeout().toMillis());
    Timeout total =
        Timeout.ofMilliseconds(
            request.connectTimeout().toMillis() + request.readTimeout().toMillis());
    try (CloseableHttpClient client =
        PinnedHttpClientFactory.build(request.pinned(), connect, read, total)) {
      HttpUriRequestBase httpReq = buildRequest(request);
      return client.execute(httpReq, response -> readResponse(response, request.maxBodyBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static HttpUriRequestBase buildRequest(Request request) {
    HttpUriRequestBase httpReq;
    switch (request.method()) {
      case GET -> httpReq = new HttpGet(request.uri());
      case POST -> {
        HttpPost post = new HttpPost(request.uri());
        if (request.body() != null) {
          ContentType ct =
              request.bodyContentType() == null
                  ? ContentType.APPLICATION_OCTET_STREAM
                  : ContentType.parse(request.bodyContentType());
          post.setEntity(new ByteArrayEntity(request.body(), ct));
        }
        httpReq = post;
      }
      default -> throw new IllegalArgumentException("unsupported method: " + request.method());
    }
    for (Map.Entry<String, String> h : request.headers().entrySet()) {
      httpReq.setHeader(h.getKey(), h.getValue());
    }
    return httpReq;
  }

  private static Response readResponse(
      org.apache.hc.core5.http.ClassicHttpResponse response, int maxBodyBytes) throws IOException {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (Header h : response.getHeaders()) {
      headers.computeIfAbsent(h.getName(), k -> new ArrayList<>()).add(h.getValue());
    }
    byte[] body;
    if (response.getEntity() == null) {
      body = new byte[0];
    } else {
      try (InputStream in = response.getEntity().getContent()) {
        body = readUpTo(in, maxBodyBytes);
      }
    }
    return new Response(response.getCode(), headers, body);
  }

  private static byte[] readUpTo(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 8192));
    byte[] buf = new byte[8192];
    int total = 0;
    while (total < max) {
      int n = in.read(buf, 0, Math.min(buf.length, max - total));
      if (n <= 0) break;
      out.write(buf, 0, n);
      total += n;
    }
    return out.toByteArray();
  }
}
