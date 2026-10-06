package com.example.short_link.federation.application.inbox;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.RemoteActorResolver;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.PemKeys;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// The Worker and nginx rewrite Host to the origin, so "host" is checked against the canonical
// federation host the sender actually signed. A failed signature refetches the key once (the
// remote may have rotated it), but not again within REFETCH_AFTER, so forged requests naming a
// real key cannot make us hammer that server.
@Service
public class InboxVerifier {

  static final Duration CLOCK_SKEW = Duration.ofHours(1);
  static final Duration REFETCH_AFTER = Duration.ofMinutes(10);
  private static final List<String> REQUIRED =
      List.of(HttpSignatures.REQUEST_TARGET, "host", "date", "digest");

  private final RemoteActorResolver resolver;
  private final RemoteActorRepository actors;
  private final String canonicalHost;
  private final Clock clock;

  @Autowired
  public InboxVerifier(
      RemoteActorResolver resolver, RemoteActorRepository actors, FederationProperties props) {
    this(resolver, actors, props, Clock.systemUTC());
  }

  InboxVerifier(
      RemoteActorResolver resolver,
      RemoteActorRepository actors,
      FederationProperties props,
      Clock clock) {
    this.resolver = resolver;
    this.actors = actors;
    this.canonicalHost = HttpSignatures.host(URI.create(props.baseUrl()));
    this.clock = clock;
  }

  public sealed interface Result {
    record Verified(RemoteActorEntity actor) implements Result {}

    record Rejected(String reason) implements Result {}
  }

  public Result verify(InboxMessage request, boolean cachedKeyOnly) {
    Optional<HttpSignatures.Params> parsed = HttpSignatures.parse(signatureHeader(request));
    if (parsed.isEmpty()) {
      return new Result.Rejected("signature");
    }
    HttpSignatures.Params params = parsed.get();
    if (!params.headers().containsAll(REQUIRED)) {
      return new Result.Rejected("signed-headers");
    }
    if (!withinClockSkew(request.header("date"))) {
      return new Result.Rejected("date");
    }
    if (!digestMatches(request.header("digest"), request.body())) {
      return new Result.Rejected("digest");
    }
    Function<String, String> values =
        name ->
            switch (name) {
              case HttpSignatures.REQUEST_TARGET ->
                  HttpSignatures.requestTarget("POST", request.path(), request.query());
              case "host" -> canonicalHost;
              default -> request.header(name);
            };
    Optional<RemoteActorEntity> actor =
        cachedKeyOnly
            ? actors.findByKeyId(params.keyId())
            : resolver.byKeyId(params.keyId(), false);
    if (actor.isEmpty()) {
      return new Result.Rejected("unknown-key");
    }
    if (signedBy(params, values, actor.get())) {
      return new Result.Verified(actor.get());
    }
    if (cachedKeyOnly || actor.get().getFetchedAt().plus(REFETCH_AFTER).isAfter(clock.instant())) {
      return new Result.Rejected("signature-mismatch");
    }
    return resolver
        .byKeyId(params.keyId(), true)
        .filter(refetched -> signedBy(params, values, refetched))
        .<Result>map(Result.Verified::new)
        .orElseGet(() -> new Result.Rejected("signature-mismatch"));
  }

  private static String signatureHeader(InboxMessage request) {
    String signature = request.header("signature");
    if (signature != null) {
      return signature;
    }
    String authorization = request.header("authorization");
    return authorization != null && authorization.regionMatches(true, 0, "Signature ", 0, 10)
        ? authorization.substring(10)
        : null;
  }

  private boolean withinClockSkew(String date) {
    if (date == null) {
      return false;
    }
    try {
      Instant sent = HttpSignatures.parseHttpDate(date);
      return Duration.between(sent, clock.instant()).abs().compareTo(CLOCK_SKEW) <= 0;
    } catch (DateTimeParseException malformed) {
      return false;
    }
  }

  static boolean digestMatches(String header, byte[] body) {
    if (header == null) {
      return false;
    }
    String expected = HttpSignatures.digest(body).substring("SHA-256=".length());
    for (String part : header.split(",")) {
      int eq = part.indexOf('=');
      if (eq > 0 && part.substring(0, eq).strip().toLowerCase(Locale.ROOT).equals("sha-256")) {
        return MessageDigest.isEqual(
            part.substring(eq + 1).strip().getBytes(StandardCharsets.US_ASCII),
            expected.getBytes(StandardCharsets.US_ASCII));
      }
    }
    return false;
  }

  private static boolean signedBy(
      HttpSignatures.Params params, Function<String, String> values, RemoteActorEntity actor) {
    return PemKeys.publicKey(actor.getPublicKeyPem())
        .map(key -> HttpSignatures.verify(params, values, key))
        .orElse(false);
  }
}
