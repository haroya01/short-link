package com.example.short_link.federation.application.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.federation.application.ActorKeys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpSignaturesTest {

  // Signed independently with openssl (dgst -sha256 -sign) over the signing string below.
  private static final String OPENSSL_PUBLIC_KEY =
      "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnUWBWZV4FMMm5GBN20Zl\nYPgDtjEhCeGgB32uBOeBFH17TWbYtqcfwzk0ZRbVCbKp+afxNpFQ9yD/NyES3jGX\nVcYGQilQVH+plABXF7kbnrzWWYysyc0/05vcbpSwsqlJrBFKBwDL0P+ve8RMcKFb\n6IYv3bWb3MNFPRh/lettR89tWTZUbhNhl6aGTe9B/0TL9ZlFJ/rfme5J5G4cTF9/\nymP7EiL69oMIxf1JqT1WOjyPbIf/316jgdcP+vZGTHKcLWuGUgtDKiVDHb3bYtI1\ntMkL9m4LMc1ldMOoije+fmKt6WjuLre/etGWiN0USCwXOBuFZU62ul7xlrDAjicj\nDwIDAQAB\n-----END PUBLIC KEY-----";
  private static final String OPENSSL_SIGNATURE =
      "L6cWj4RvsU0kCd8MV00Hwv+sYZFdCo/obv1BW/sNNDIaMOOx5pa5GnnjT5lzADsOzshg2am+g3Gqt/7Fwg/YWZFnsRgWOvcK0ZjAKFNCbJGW0QrViOOisodavj1ankrrHmLUK6JIyN8ejwdTT8bD+HptFs4d9KhqortjQPkIFAjXZxoS3m885thG5fYoiCDCqUKrT4Lf0xu6Gxivj7PW62IiZqHZVJNg7KACMd16ru3bv4I4iq9RQm9VwCDQINxzsh7whF0a4XAs7wbJBPuaPYzo45q1CoMKzaLikdE3fcOO5yPpvitTcbXe3kIqOViqetBdH8+dK90R3kU9M/ofTQ==";

  private static final Map<String, String> SIGNED =
      Map.of(
          "(request-target)", "post /ap/actors/abc/inbox",
          "host", "kurl.me",
          "date", "Mon, 05 Oct 2026 12:00:00 GMT",
          "digest", "SHA-256=LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=");

  @Test
  void verifiesASignatureMadeByAnIndependentSigner() {
    var params =
        HttpSignatures.parse(
                "keyId=\"https://remote.example/users/a#main-key\",algorithm=\"rsa-sha256\","
                    + "headers=\"(request-target) host date digest\",signature=\""
                    + OPENSSL_SIGNATURE
                    + "\"")
            .orElseThrow();

    assertThat(
            HttpSignatures.verify(
                params, SIGNED::get, PemKeys.publicKey(OPENSSL_PUBLIC_KEY).orElseThrow()))
        .isTrue();
  }

  @Test
  void signingStringMatchesTheDraftLayout() {
    assertThat(HttpSignatures.signingString(HttpSignatures.POST_HEADERS, SIGNED::get))
        .isEqualTo(
            "(request-target): post /ap/actors/abc/inbox\n"
                + "host: kurl.me\n"
                + "date: Mon, 05 Oct 2026 12:00:00 GMT\n"
                + "digest: SHA-256=LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=");
    assertThatThrownBy(
            () -> HttpSignatures.signingString(java.util.List.of("x-missing"), SIGNED::get))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ourSignatureVerifiesAndAnyTamperingBreaksIt() {
    ActorKeys.Pem pem = new ActorKeys().generate();
    String header =
        HttpSignatures.sign(
            "https://kurl.me/ap/actors/abc#main-key",
            PemKeys.privateKey(pem.privateKey()),
            HttpSignatures.POST_HEADERS,
            SIGNED::get);
    var params = HttpSignatures.parse(header).orElseThrow();
    var key = PemKeys.publicKey(pem.publicKey()).orElseThrow();

    assertThat(params.keyId()).isEqualTo("https://kurl.me/ap/actors/abc#main-key");
    assertThat(params.headers()).containsExactly("(request-target)", "host", "date", "digest");
    assertThat(HttpSignatures.verify(params, SIGNED::get, key)).isTrue();

    Map<String, String> tampered = new java.util.HashMap<>(SIGNED);
    tampered.put("digest", HttpSignatures.digest("other".getBytes(StandardCharsets.UTF_8)));
    assertThat(HttpSignatures.verify(params, tampered::get, key)).isFalse();
    Map<String, String> missing = new java.util.HashMap<>(SIGNED);
    missing.remove("date");
    assertThat(HttpSignatures.verify(params, missing::get, key)).isFalse();
  }

  @Test
  void parsesTheVariantsServersSend() {
    var hs2019 =
        HttpSignatures.parse(
                "keyId=\"k\", algorithm=\"hs2019\", headers=\"(request-target) host date\", signature=\"AAAA\"")
            .orElseThrow();
    assertThat(hs2019.algorithm()).isEqualTo("hs2019");
    assertThat(hs2019.headers()).containsExactly("(request-target)", "host", "date");

    var defaults = HttpSignatures.parse("keyId=\"k\",signature=\"AAAA\"").orElseThrow();
    assertThat(defaults.algorithm()).isEqualTo("rsa-sha256");
    assertThat(defaults.headers()).containsExactly("date");

    assertThat(HttpSignatures.parse("keyId=\"k\",algorithm=\"hmac-sha256\",signature=\"AAAA\""))
        .isEmpty();
    assertThat(HttpSignatures.parse("keyId=\"k\",signature=\"not base64!\"")).isEmpty();
    assertThat(HttpSignatures.parse("signature=\"AAAA\"")).isEmpty();
    assertThat(HttpSignatures.parse("keyId=\"k\",signature=\"unterminated")).isEmpty();
    assertThat(HttpSignatures.parse("nonsense")).isEmpty();
    assertThat(HttpSignatures.parse("")).isEmpty();
    assertThat(HttpSignatures.parse(null)).isEmpty();
  }

  @Test
  void headerValuesMatchWhatHttpClientsSend() {
    assertThat(HttpSignatures.digest("hello".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("SHA-256=LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ=");
    assertThat(HttpSignatures.httpDate(Instant.parse("2026-10-05T12:00:00Z")))
        .isEqualTo("Mon, 05 Oct 2026 12:00:00 GMT");
    assertThat(HttpSignatures.parseHttpDate("Mon, 05 Oct 2026 12:00:00 GMT"))
        .isEqualTo(Instant.parse("2026-10-05T12:00:00Z"));
    assertThat(HttpSignatures.host(URI.create("https://kurl.me/ap"))).isEqualTo("kurl.me");
    assertThat(HttpSignatures.host(URI.create("https://kurl.me:443/ap"))).isEqualTo("kurl.me");
    assertThat(HttpSignatures.host(URI.create("http://kurl.me:80/ap"))).isEqualTo("kurl.me");
    assertThat(HttpSignatures.host(URI.create("http://localhost:8080/ap")))
        .isEqualTo("localhost:8080");
    assertThat(HttpSignatures.requestTarget("POST", "/ap/inbox", null)).isEqualTo("post /ap/inbox");
    assertThat(HttpSignatures.requestTarget("GET", "/users/a", "page=2"))
        .isEqualTo("get /users/a?page=2");
    assertThat(HttpSignatures.requestTarget("GET", "", null)).isEqualTo("get /");
  }

  @Test
  void pemParsingRejectsGarbage() {
    assertThat(PemKeys.publicKey("not a key")).isEmpty();
    assertThat(PemKeys.publicKey(null)).isEmpty();
    assertThat(PemKeys.publicKey("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"))
        .isEmpty();
    assertThatThrownBy(
            () ->
                PemKeys.privateKey("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"))
        .isInstanceOf(IllegalStateException.class);
  }
}
