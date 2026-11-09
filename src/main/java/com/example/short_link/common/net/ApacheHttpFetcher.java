package com.example.short_link.common.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

// Uses a fresh pinned client per request to prevent DNS rebinding. Connections are deliberately not
// pooled across resolved IP batches.
@Component
public class ApacheHttpFetcher implements HttpFetcher {

  private static final ScheduledExecutorService DEADLINES =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("http-fetch-deadline").daemon().factory());

  @Override
  public Response fetch(Request request) {
    Timeout connect = Timeout.ofMilliseconds(request.connectTimeout().toMillis());
    Timeout read = Timeout.ofMilliseconds(request.readTimeout().toMillis());
    Timeout total =
        Timeout.ofMilliseconds(
            request.connectTimeout().toMillis() + request.readTimeout().toMillis());
    HttpUriRequestBase httpReq = buildRequest(request);
    ScheduledFuture<?> deadline =
        DEADLINES.schedule(httpReq::cancel, total.toMilliseconds(), TimeUnit.MILLISECONDS);
    try (CloseableHttpClient client =
        PinnedHttpClientFactory.build(
            request.pinned(), connect, read, total, request.followRedirects())) {
      ClassicHttpResponse response = client.executeOpen(null, httpReq, null);
      try {
        return readResponse(response, request.maxBodyBytes());
      } finally {
        httpReq.cancel();
        closeQuietly(response);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      deadline.cancel(false);
    }
  }

  private static HttpUriRequestBase buildRequest(Request request) {
    HttpUriRequestBase httpReq;
    switch (request.method()) {
      case GET -> httpReq = new HttpGet(request.uri());
      case POST -> {
        HttpPost post = new HttpPost(request.uri());
        ContentType ct =
            request.bodyContentType() == null
                ? ContentType.APPLICATION_OCTET_STREAM
                : ContentType.parse(request.bodyContentType());
        post.setEntity(new ByteArrayEntity(request.body(), ct));
        httpReq = post;
      }
      default -> throw new IllegalArgumentException("unsupported method: " + request.method());
    }
    for (Map.Entry<String, String> h : request.headers().entrySet()) {
      httpReq.setHeader(h.getKey(), h.getValue());
    }
    return httpReq;
  }

  private static Response readResponse(ClassicHttpResponse response, int maxBodyBytes)
      throws IOException {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (Header h : response.getHeaders()) {
      headers.computeIfAbsent(h.getName(), k -> new ArrayList<>()).add(h.getValue());
    }
    byte[] body =
        response.getEntity() == null
            ? new byte[0]
            : readUpTo(response.getEntity().getContent(), maxBodyBytes);
    return new Response(response.getCode(), headers, body);
  }

  private static void closeQuietly(ClassicHttpResponse response) {
    try {
      response.close();
    } catch (IOException ignored) {
      return;
    }
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
