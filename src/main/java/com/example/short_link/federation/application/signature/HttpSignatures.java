package com.example.short_link.federation.application.signature;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// draft-cavage-http-signatures-12 with rsa-sha256, the scheme Mastodon, Misskey and GoToSocial
// sign and accept. "hs2019" is the same RSA-SHA256 computation when the key is RSA.
public final class HttpSignatures {

  public static final String REQUEST_TARGET = "(request-target)";
  public static final List<String> GET_HEADERS = List.of(REQUEST_TARGET, "host", "date");
  public static final List<String> POST_HEADERS = List.of(REQUEST_TARGET, "host", "date", "digest");

  private static final DateTimeFormatter HTTP_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
          .withZone(ZoneOffset.UTC);

  private HttpSignatures() {}

  public record Params(String keyId, String algorithm, List<String> headers, byte[] signature) {}

  public static String httpDate(Instant instant) {
    return HTTP_DATE.format(instant);
  }

  public static Instant parseHttpDate(String value) {
    return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value.trim()));
  }

  public static String digest(byte[] body) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
      return "SHA-256=" + Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  public static String host(URI uri) {
    int port = uri.getPort();
    boolean defaultPort =
        port == -1
            || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
            || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80);
    return defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
  }

  public static String requestTarget(String method, String rawPath, String rawQuery) {
    String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
    return method.toLowerCase(Locale.ROOT)
        + " "
        + (rawQuery == null || rawQuery.isEmpty() ? path : path + "?" + rawQuery);
  }

  public static String signingString(List<String> headers, Function<String, String> values) {
    List<String> lines = new ArrayList<>(headers.size());
    for (String header : headers) {
      String name = header.toLowerCase(Locale.ROOT);
      String value = values.apply(name);
      if (value == null) {
        throw new IllegalArgumentException("missing signed header: " + name);
      }
      lines.add(name + ": " + value.strip());
    }
    return String.join("\n", lines);
  }

  public static String sign(
      String keyId, PrivateKey key, List<String> headers, Function<String, String> values) {
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(key);
      signer.update(signingString(headers, values).getBytes(StandardCharsets.UTF_8));
      String signature = Base64.getEncoder().encodeToString(signer.sign());
      return "keyId=\""
          + keyId
          + "\",algorithm=\"rsa-sha256\",headers=\""
          + String.join(" ", headers)
          + "\",signature=\""
          + signature
          + "\"";
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("cannot sign request", e);
    }
  }

  public static Optional<Params> parse(String header) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    Map<String, String> fields = new LinkedHashMap<>();
    int i = 0;
    String value = header.trim();
    while (i < value.length()) {
      int eq = value.indexOf('=', i);
      if (eq < 0) {
        return Optional.empty();
      }
      String name = value.substring(i, eq).trim().toLowerCase(Locale.ROOT);
      int start = eq + 1;
      String field;
      int next;
      if (start < value.length() && value.charAt(start) == '"') {
        int end = value.indexOf('"', start + 1);
        if (end < 0) {
          return Optional.empty();
        }
        field = value.substring(start + 1, end);
        next = value.indexOf(',', end);
      } else {
        next = value.indexOf(',', start);
        field = (next < 0 ? value.substring(start) : value.substring(start, next)).trim();
      }
      fields.putIfAbsent(name, field);
      if (next < 0) {
        break;
      }
      i = next + 1;
    }
    String keyId = fields.get("keyid");
    String signature = fields.get("signature");
    if (keyId == null || keyId.isBlank() || signature == null || signature.isBlank()) {
      return Optional.empty();
    }
    String algorithm = fields.getOrDefault("algorithm", "rsa-sha256").toLowerCase(Locale.ROOT);
    if (!algorithm.equals("rsa-sha256") && !algorithm.equals("hs2019")) {
      return Optional.empty();
    }
    String headerList = fields.getOrDefault("headers", "date");
    List<String> headers = List.of(headerList.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    try {
      return Optional.of(
          new Params(keyId, algorithm, headers, Base64.getDecoder().decode(signature)));
    } catch (IllegalArgumentException notBase64) {
      return Optional.empty();
    }
  }

  public static boolean verify(Params params, Function<String, String> values, PublicKey key) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(signingString(params.headers(), values).getBytes(StandardCharsets.UTF_8));
      return verifier.verify(params.signature());
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return false;
    }
  }
}
