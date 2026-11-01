package com.example.short_link.common.net;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PublicImageFetcher {

  public static final String JPEG = "image/jpeg";
  public static final String PNG = "image/png";
  private static final String ACCEPTED = JPEG + ", " + PNG;

  private static final String USER_AGENT = "kurl-media/1.0 (+https://kurl.me/bot)";
  private static final int MAX_REDIRECTS = 3;
  private static final int CHUNK = 16 * 1024;
  private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
  };

  private final Function<String, Optional<Resolved>> resolver;
  private final String scheme;
  private final Timeout connectTimeout;
  private final Timeout readTimeout;
  private final Duration deadline;

  @Autowired
  public PublicImageFetcher() {
    this(
        PublicHttpUrlGuard::resolve,
        "https",
        Duration.ofSeconds(3),
        Duration.ofSeconds(5),
        Duration.ofSeconds(20));
  }

  PublicImageFetcher(
      Function<String, Optional<Resolved>> resolver,
      String scheme,
      Duration connectTimeout,
      Duration readTimeout,
      Duration deadline) {
    this.resolver = resolver;
    this.scheme = scheme;
    this.connectTimeout = Timeout.ofMilliseconds(connectTimeout.toMillis());
    this.readTimeout = Timeout.ofMilliseconds(readTimeout.toMillis());
    this.deadline = deadline;
  }

  public Result fetch(String url, int maxBytes) {
    long deadlineNanos = System.nanoTime() + deadline.toNanos();
    String next = url;
    for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
      Optional<Resolved> target = allowed(next);
      if (target.isEmpty()) {
        return new Refused("not a public " + scheme + " address");
      }
      Step step = get(target.get(), maxBytes, deadlineNanos);
      if (step.location() == null) {
        return step.result();
      }
      next = step.location();
    }
    return new Refused("too many redirects");
  }

  private Optional<Resolved> allowed(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException | NullPointerException malformed) {
      return Optional.empty();
    }
    if (!scheme.equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) {
      return Optional.empty();
    }
    return resolver.apply(url);
  }

  private Step get(Resolved target, int maxBytes, long deadlineNanos) {
    HttpGet request = new HttpGet(target.uri());
    request.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
    request.setHeader(HttpHeaders.ACCEPT, ACCEPTED);
    try (CloseableHttpClient client =
        PinnedHttpClientFactory.build(target, connectTimeout, readTimeout, readTimeout, false)) {
      ClassicHttpResponse response = client.executeOpen(null, request, null);
      try {
        return answer(target.uri(), response, maxBytes, deadlineNanos);
      } finally {
        request.cancel();
        closeQuietly(response);
      }
    } catch (IOException | RuntimeException failed) {
      return new Step(new Refused(failed.getClass().getSimpleName()), null);
    }
  }

  private static Step answer(
      URI from, ClassicHttpResponse response, int maxBytes, long deadlineNanos) throws IOException {
    int status = response.getCode();
    if (REDIRECTS.contains(status)) {
      return redirect(from, response.getFirstHeader(HttpHeaders.LOCATION));
    }
    HttpEntity entity = response.getEntity();
    if (status != HttpStatus.SC_OK || entity == null) {
      return done(new Refused("HTTP " + status));
    }
    String served = mediaType(entity.getContentType());
    if (!JPEG.equals(served) && !PNG.equals(served)) {
      return done(new Refused("served as " + served));
    }
    if (entity.getContentLength() > maxBytes) {
      return done(new Refused("larger than " + maxBytes + " bytes"));
    }
    Result body = read(entity, served, maxBytes, deadlineNanos);
    if (body instanceof Image image && !served.equals(sniff(image.bytes()))) {
      return done(new Refused("bytes are not " + served));
    }
    return done(body);
  }

  private static Result read(HttpEntity entity, String type, int maxBytes, long deadlineNanos)
      throws IOException {
    long length = entity.getContentLength();
    ByteArrayOutputStream out = new ByteArrayOutputStream(length > 0 ? (int) length : CHUNK);
    byte[] chunk = new byte[CHUNK];
    InputStream in = entity.getContent();
    for (int n = in.read(chunk); n != -1; n = in.read(chunk)) {
      if (out.size() + n > maxBytes) {
        return new Refused("larger than " + maxBytes + " bytes");
      }
      if (System.nanoTime() - deadlineNanos > 0) {
        return new Refused("slower than the deadline");
      }
      out.write(chunk, 0, n);
    }
    return new Image(out.toByteArray(), type);
  }

  private static Step redirect(URI from, Header location) {
    if (location == null || location.getValue() == null || location.getValue().isBlank()) {
      return done(new Refused("redirect without a location"));
    }
    try {
      return new Step(null, from.resolve(location.getValue().strip()).toString());
    } catch (IllegalArgumentException malformed) {
      return done(new Refused("malformed redirect"));
    }
  }

  private static String mediaType(String header) {
    if (header == null) {
      return "none";
    }
    int params = header.indexOf(';');
    return (params < 0 ? header : header.substring(0, params)).strip().toLowerCase(Locale.ROOT);
  }

  private static String sniff(byte[] body) {
    if (startsWith(body, JPEG_SIGNATURE)) {
      return JPEG;
    }
    return startsWith(body, PNG_SIGNATURE) ? PNG : "unknown";
  }

  private static boolean startsWith(byte[] body, byte[] signature) {
    return body.length >= signature.length
        && Arrays.equals(body, 0, signature.length, signature, 0, signature.length);
  }

  private static void closeQuietly(ClassicHttpResponse response) {
    try {
      response.close();
    } catch (IOException ignored) {
      return;
    }
  }

  private static Step done(Result result) {
    return new Step(result, null);
  }

  private record Step(Result result, String location) {}

  public sealed interface Result permits Image, Refused {}

  public record Image(byte[] bytes, String type) implements Result {}

  public record Refused(String reason) implements Result {}
}
