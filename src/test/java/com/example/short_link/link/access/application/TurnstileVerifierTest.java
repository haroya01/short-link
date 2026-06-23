package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TurnstileVerifierTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private TurnstileVerifier noEndpoint(String secret) {
    return new TurnstileVerifier(new TurnstileProperties("site", secret));
  }

  /** body 를 돌려주는 로컬 스텁을 띄우고, 그 엔드포인트를 검증기에 주입한다. */
  private TurnstileVerifier stub(
      String secret, int status, String body, AtomicReference<String> seen) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          byte[] in = exchange.getRequestBody().readAllBytes();
          if (seen != null) {
            seen.set(new String(in, StandardCharsets.UTF_8));
          }
          byte[] out = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
    URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    return new TurnstileVerifier(new TurnstileProperties("site", secret), endpoint);
  }

  @Test
  void verify_passesWhenSecretUnset() {
    TurnstileVerifier verifier = noEndpoint("");

    assertThat(verifier.enabled()).isFalse();
    // 미설정이면 토큰이 없어도 통과 — 게이트는 비밀번호로 계속 동작한다.
    assertThat(verifier.verify(null, null)).isTrue();
    assertThat(verifier.verify("anything", "1.2.3.4")).isTrue();
  }

  @Test
  void verify_failsClosedOnMissingToken_whenConfigured() {
    TurnstileVerifier verifier = noEndpoint("0xSECRET");

    assertThat(verifier.enabled()).isTrue();
    assertThat(verifier.verify(null, null)).isFalse();
    assertThat(verifier.verify("   ", null)).isFalse();
  }

  @Test
  void verify_acceptsSuccessResponse_andSendsRemoteIp() throws Exception {
    AtomicReference<String> form = new AtomicReference<>();
    TurnstileVerifier verifier = stub("0xSECRET", 200, "{\"success\":true}", form);

    assertThat(verifier.verify("good-token", "203.0.113.7")).isTrue();
    assertThat(form.get()).contains("response=good-token");
    assertThat(form.get()).contains("secret=0xSECRET");
    assertThat(form.get()).contains("remoteip=203.0.113.7");
  }

  @Test
  void verify_omitsRemoteIp_whenBlank() throws Exception {
    AtomicReference<String> form = new AtomicReference<>();
    TurnstileVerifier verifier = stub("0xSECRET", 200, "{\"success\":true}", form);

    assertThat(verifier.verify("good-token", "  ")).isTrue();
    assertThat(form.get()).doesNotContain("remoteip");
  }

  @Test
  void verify_rejectsUnsuccessfulBody() throws Exception {
    TurnstileVerifier verifier = stub("0xSECRET", 200, "{\"success\":false}", null);

    assertThat(verifier.verify("bad-token", null)).isFalse();
  }

  @Test
  void verify_rejectsNon200() throws Exception {
    TurnstileVerifier verifier = stub("0xSECRET", 500, "nope", null);

    assertThat(verifier.verify("any-token", null)).isFalse();
  }

  @Test
  void verify_failsClosedOnTransportError() {
    // 닫힌 포트로 보내 연결 자체가 실패 → catch → false.
    TurnstileVerifier verifier =
        new TurnstileVerifier(
            new TurnstileProperties("site", "0xSECRET"), URI.create("http://127.0.0.1:1/"));

    assertThat(verifier.verify("token", null)).isFalse();
  }
}
