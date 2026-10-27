package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ApacheHttpFetcherTest {

  private static final Duration CONNECT = Duration.ofMillis(500);
  private static final Duration READ = Duration.ofSeconds(2);

  private final ApacheHttpFetcher fetcher = new ApacheHttpFetcher();
  private final AtomicInteger dropped = new AtomicInteger();
  private HttpServer server;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/ok", exchange -> respond(exchange, 200, "hello"));
    server.createContext("/same", exchange -> redirect(exchange, "/ok"));
    server.createContext(
        "/elsewhere", exchange -> redirect(exchange, "http://other.invalid:" + port() + "/ok"));
    server.createContext(
        "/echo",
        exchange ->
            respond(
                exchange,
                200,
                exchange.getRequestHeaders().getFirst("Content-Type")
                    + " "
                    + exchange.getRequestHeaders().getFirst("X-Kurl-Event")
                    + " "
                    + new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
    server.createContext(
        "/drop",
        exchange -> {
          dropped.incrementAndGet();
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private int port() {
    return server.getAddress().getPort();
  }

  private Resolved pinned(String scheme, int port, String path) {
    return new Resolved(
        URI.create(scheme + "://pinned.invalid:" + port + path),
        List.of(InetAddress.getLoopbackAddress()));
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private static String text(Response response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  @Test
  void aRequestGoesToThePinnedAddressWithoutAskingDns() {
    Response response =
        fetcher.fetch(Request.get(pinned("http", port(), "/ok"), Map.of(), CONNECT, READ, 1_000));

    assertThat(response.status()).isEqualTo(200);
    assertThat(text(response)).isEqualTo("hello");
  }

  @Test
  void aRedirectOnTheSameHostIsFollowed() {
    Response response =
        fetcher.fetch(Request.get(pinned("http", port(), "/same"), Map.of(), CONNECT, READ, 1_000));

    assertThat(response.status()).isEqualTo(200);
    assertThat(text(response)).isEqualTo("hello");
  }

  @Test
  void aRedirectToAnotherHostIsRefusedByThePinnedResolver() {
    assertThatThrownBy(
            () ->
                fetcher.fetch(
                    Request.get(
                        pinned("http", port(), "/elsewhere"), Map.of(), CONNECT, READ, 1_000)))
        .isInstanceOf(UncheckedIOException.class)
        .cause()
        .isInstanceOf(UnknownHostException.class)
        .hasMessageContaining("refused other.invalid");
  }

  @Test
  void withoutRedirectsTheLocationComesBackToTheCaller() {
    Response response =
        fetcher.fetch(
            Request.getNoRedirects(
                pinned("http", port(), "/elsewhere"), Map.of(), CONNECT, READ, 1_000));

    assertThat(response.status()).isEqualTo(302);
    assertThat(response.headers().get("Location"))
        .containsExactly("http://other.invalid:" + port() + "/ok");
  }

  @Test
  void aPostCarriesItsHeadersBodyAndType() {
    Response response =
        fetcher.fetch(
            Request.post(
                pinned("http", port(), "/echo"),
                Map.of("X-Kurl-Event", "link.click"),
                "{}".getBytes(StandardCharsets.UTF_8),
                "application/json",
                CONNECT,
                READ,
                1_000));

    assertThat(text(response)).isEqualTo("application/json link.click {}");
  }

  @Test
  void theBodyIsCutAtTheCap() {
    Response response =
        fetcher.fetch(Request.get(pinned("http", port(), "/ok"), Map.of(), CONNECT, READ, 3));

    assertThat(text(response)).isEqualTo("hel");
  }

  @Test
  void aDroppedConnectionIsNotRetried() {
    assertThatThrownBy(
            () ->
                fetcher.fetch(
                    Request.get(pinned("http", port(), "/drop"), Map.of(), CONNECT, READ, 1_000)))
        .isInstanceOf(UncheckedIOException.class);
    assertThat(dropped).hasValue(1);
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS)
  void aStalledTlsHandshakeGivesUpAtTheConnectTimeout() throws IOException {
    try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      Thread.ofVirtual()
          .start(
              () -> {
                try (Socket held = silent.accept()) {
                  Thread.sleep(Duration.ofSeconds(30));
                } catch (IOException | InterruptedException ignored) {
                }
              });
      long started = System.nanoTime();

      assertThatThrownBy(
              () ->
                  fetcher.fetch(
                      Request.get(
                          pinned("https", silent.getLocalPort(), "/"),
                          Map.of(),
                          CONNECT,
                          Duration.ofSeconds(10),
                          1_000)))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }
  }
}
