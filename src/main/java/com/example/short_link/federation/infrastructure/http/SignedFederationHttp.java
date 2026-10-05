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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SignedFederationHttp implements FederationHttp {

  static final String ACTIVITY_JSON = "application/activity+json";
  private static final String ACCEPT =
      "application/activity+json, application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_BODY_BYTES = 1_048_576;

  private final HttpFetcher fetcher;
  private final boolean allowPlainHttp;
  private final Clock clock;

  @Autowired
  public SignedFederationHttp(HttpFetcher fetcher, FederationProperties props) {
    this(fetcher, props, Clock.systemUTC());
  }

  SignedFederationHttp(HttpFetcher fetcher, FederationProperties props, Clock clock) {
    this.fetcher = fetcher;
    this.allowPlainHttp = props.baseUrl().startsWith("http://");
    this.clock = clock;
  }

  @Override
  public Result get(URI uri, Signer signer) {
    return send(uri, null, signer);
  }

  @Override
  public Result post(URI inbox, byte[] body, Signer signer) {
    return send(inbox, body, signer);
  }

  private Result send(URI uri, byte[] body, Signer signer) {
    if (!"https".equalsIgnoreCase(uri.getScheme())
        && !(allowPlainHttp && "http".equalsIgnoreCase(uri.getScheme()))) {
      return new Result.Refused("scheme " + uri.getScheme());
    }
    var resolved = PublicHttpUrlGuard.resolve(uri.toString());
    if (resolved.isEmpty()) {
      return new Result.Refused("not a public address");
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
    headers.put("Accept", ACCEPT);
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
                      CONNECT_TIMEOUT,
                      READ_TIMEOUT,
                      MAX_BODY_BYTES)
                  : HttpFetcher.Request.get(
                      resolved.get(), headers, CONNECT_TIMEOUT, READ_TIMEOUT, MAX_BODY_BYTES));
    } catch (RuntimeException e) {
      return new Result.Unreachable(e.getClass().getSimpleName());
    }
    int status = response.status();
    if (status >= 200 && status < 300) {
      return new Result.Ok(status, response.body());
    }
    return new Result.Failed(status, "HTTP " + status);
  }
}
