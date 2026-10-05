package com.example.short_link.federation.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.application.signature.PemKeys;
import com.example.short_link.federation.application.signature.Signer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignedFederationHttpTest {

  // A public IP literal: the address guard accepts it without a DNS lookup.
  private static final URI INBOX = URI.create("https://93.184.216.34/users/a/inbox");
  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
  private static final ActorKeys.Pem PEM = new ActorKeys().generate();
  private static final Signer SIGNER =
      new Signer("https://kurl.me/ap/actors/x#main-key", PemKeys.privateKey(PEM.privateKey()));

  @Mock private HttpFetcher fetcher;

  private SignedFederationHttp http(String baseUrl) {
    return new SignedFederationHttp(
        fetcher, new FederationProperties(baseUrl, null), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void postsAreSignedSoTheReceiverCanVerifyThem() {
    when(fetcher.fetch(any())).thenReturn(new HttpFetcher.Response(202, Map.of(), new byte[0]));
    byte[] body = "{\"type\":\"Accept\"}".getBytes(StandardCharsets.UTF_8);

    var result = http("https://kurl.me").post(INBOX, body, SIGNER);

    assertThat(result).isInstanceOf(FederationHttp.Result.Ok.class);
    assertThat(((FederationHttp.Result.Ok) result).status()).isEqualTo(202);
    ArgumentCaptor<HttpFetcher.Request> sent = ArgumentCaptor.forClass(HttpFetcher.Request.class);
    verify(fetcher).fetch(sent.capture());
    var request = sent.getValue();
    assertThat(request.method()).isEqualTo(HttpFetcher.Method.POST);
    assertThat(request.bodyContentType()).isEqualTo("application/activity+json");
    assertThat(request.headers().get("Date")).isEqualTo("Tue, 06 Oct 2026 00:00:00 GMT");
    assertThat(request.headers().get("Digest")).isEqualTo(HttpSignatures.digest(body));

    var params = HttpSignatures.parse(request.headers().get("Signature")).orElseThrow();
    Map<String, String> received =
        Map.of(
            "(request-target)",
            "post /users/a/inbox",
            "host",
            "93.184.216.34",
            "date",
            request.headers().get("Date"),
            "digest",
            request.headers().get("Digest"));
    assertThat(params.headers()).containsExactly("(request-target)", "host", "date", "digest");
    assertThat(
            HttpSignatures.verify(
                params, received::get, PemKeys.publicKey(PEM.publicKey()).orElseThrow()))
        .isTrue();
  }

  @Test
  void getsAreSignedWithoutADigest() {
    when(fetcher.fetch(any()))
        .thenReturn(new HttpFetcher.Response(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8)));

    var result =
        http("https://kurl.me").get(URI.create("https://93.184.216.34/users/a?x=1"), SIGNER);

    assertThat(result).isInstanceOf(FederationHttp.Result.Ok.class);
    ArgumentCaptor<HttpFetcher.Request> sent = ArgumentCaptor.forClass(HttpFetcher.Request.class);
    verify(fetcher).fetch(sent.capture());
    assertThat(sent.getValue().headers()).doesNotContainKey("Digest");
    assertThat(sent.getValue().headers().get("Accept")).startsWith("application/activity+json");
    assertThat(
            HttpSignatures.parse(sent.getValue().headers().get("Signature"))
                .orElseThrow()
                .headers())
        .containsExactly("(request-target)", "host", "date");
  }

  @Test
  void refusesPlainHttpPrivateAddressesAndReportsFailures() {
    assertThat(http("https://kurl.me").get(URI.create("http://93.184.216.34/a"), SIGNER))
        .isInstanceOf(FederationHttp.Result.Refused.class);
    assertThat(http("https://kurl.me").get(URI.create("https://127.0.0.1/a"), SIGNER))
        .isInstanceOf(FederationHttp.Result.Refused.class);
    assertThat(http("https://kurl.me").get(URI.create("ftp://93.184.216.34/a"), SIGNER))
        .isInstanceOf(FederationHttp.Result.Refused.class);
    verify(fetcher, never()).fetch(any());

    when(fetcher.fetch(any())).thenReturn(new HttpFetcher.Response(410, Map.of(), new byte[0]));
    assertThat(http("http://localhost:8080").get(URI.create("http://93.184.216.34/a"), SIGNER))
        .isEqualTo(new FederationHttp.Result.Failed(410, "HTTP 410"));

    when(fetcher.fetch(any()))
        .thenThrow(new java.io.UncheckedIOException(new java.io.IOException("x")));
    assertThat(http("https://kurl.me").post(INBOX, new byte[] {1}, SIGNER))
        .isEqualTo(new FederationHttp.Result.Unreachable("UncheckedIOException"));
  }
}
