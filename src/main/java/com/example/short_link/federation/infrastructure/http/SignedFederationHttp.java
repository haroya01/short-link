package com.example.short_link.federation.infrastructure.http;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.Signer;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

@Component
public class SignedFederationHttp implements FederationHttp {

  static final String ACTIVITY_JSON = "application/activity+json";
  private static final String ACCEPT =
      "application/activity+json, application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";
  private static final String JRD = "application/jrd+json, application/json";
  private static final String JSON = "application/json";
  private static final int MAX_REDIRECTS = 3;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_BODY_BYTES = 1_048_576;

  private final HttpFetcher fetcher;
  private final boolean allowPlainHttp;
  private final String userAgent;
  private final Clock clock;

  public SignedFederationHttp(HttpFetcher fetcher, FederationProperties props, Clock clock) {
    this.fetcher = fetcher;
    this.allowPlainHttp = props.baseUrl().startsWith("http://");
    this.userAgent = "kurl (+" + props.baseUrl() + "/)";
    this.clock = clock;
  }

  @Override
  public Result get(URI uri, Signer signer) {
    return send(uri, null, signer, ACCEPT, true, READ_TIMEOUT).result();
  }

  @Override
  public Result get(URI uri, Signer signer, Duration patience) {
    return send(uri, null, signer, ACCEPT, true, patience).result();
  }

  @Override
  public Result getJson(URI uri, Signer signer, Duration patience) {
    return send(uri, null, signer, JSON, true, patience).result();
  }

  @Override
  public Result webFinger(URI uri, Signer signer, Predicate<String> allowedHost) {
    URI next = uri;
    for (int redirects = 0; ; redirects++) {
      if (!allowedHost.test(next.getHost())) {
        return new Result.Refused("host " + next.getHost());
      }
      Sent sent = send(next, null, signer, JRD, false, READ_TIMEOUT);
      if (sent.location() == null || redirects == MAX_REDIRECTS) {
        return sent.result();
      }
      if (!"https".equalsIgnoreCase(sent.location().getScheme())) {
        return new Result.Refused("redirect scheme " + sent.location().getScheme());
      }
      next = sent.location();
    }
  }

  @Override
  public Result post(URI inbox, byte[] body, Signer signer) {
    return send(inbox, body, signer, ACCEPT, true, READ_TIMEOUT).result();
  }

  private record Sent(Result result, URI location) {

    Sent(Result result) {
      this(result, null);
    }
  }

  private Sent send(
      URI uri,
      byte[] body,
      Signer signer,
      String accept,
      boolean followRedirects,
      Duration readTimeout) {
    Duration connectTimeout =
        readTimeout.compareTo(CONNECT_TIMEOUT) < 0 ? readTimeout : CONNECT_TIMEOUT;
    if (!"https".equalsIgnoreCase(uri.getScheme())
        && !(allowPlainHttp && "http".equalsIgnoreCase(uri.getScheme()))) {
      return new Sent(new Result.Refused("scheme " + uri.getScheme()));
    }
    var resolved = PublicHttpUrlGuard.resolve(uri.toString());
    if (resolved.isEmpty()) {
      return new Sent(new Result.Refused("not a public address"));
    }
    boolean post = body != null;
    Map<String, String> signed = new LinkedHashMap<>();
    signed.put(
        HttpSignatures.REQUEST_TARGET,
        HttpSignatures.requestTarget(post ? "POST" : "GET", uri.getRawPath(), uri.getRawQuery()));
    signed.put("host", HttpSignatures.host(uri));
    signed.put("date", HttpSignatures.httpDate(clock.instant()));
    if (post) {
      signed.put("digest", HttpSignatures.digest(body));
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Date", signed.get("date"));
    headers.put("Accept", accept);
    headers.put("User-Agent", userAgent);
    if (post) {
      headers.put("Digest", signed.get("digest"));
    }
    headers.put(
        "Signature",
        HttpSignatures.sign(
            signer.keyId(),
            signer.privateKey(),
            post ? HttpSignatures.POST_HEADERS : HttpSignatures.GET_HEADERS,
            signed::get));
    HttpFetcher.Response response;
    try {
      response =
          fetcher.fetch(
              post
                  ? HttpFetcher.Request.post(
                      resolved.get(),
                      headers,
                      body,
                      ACTIVITY_JSON,
                      connectTimeout,
                      readTimeout,
                      MAX_BODY_BYTES)
                  : followRedirects
                      ? HttpFetcher.Request.get(
                          resolved.get(), headers, connectTimeout, readTimeout, MAX_BODY_BYTES)
                      : HttpFetcher.Request.getNoRedirects(
                          resolved.get(), headers, connectTimeout, readTimeout, MAX_BODY_BYTES));
    } catch (RuntimeException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      return new Sent(new Result.Unreachable(cause.getClass().getSimpleName()));
    }
    int status = response.status();
    if (status >= 200 && status < 300) {
      return new Sent(new Result.Ok(status, response.body(), response.header("Content-Type")));
    }
    return new Sent(
        new Result.Failed(status, "HTTP " + status),
        followRedirects ? null : location(uri, status, response.header("Location")));
  }

  private static URI location(URI from, int status, String header) {
    if (status < 300 || status >= 400 || header == null || header.isBlank()) {
      return null;
    }
    try {
      return from.resolve(header.strip());
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }
}
