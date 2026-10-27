package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.common.net.HttpFetcher.Request;
import com.example.short_link.common.net.HttpFetcher.Response;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class ApacheHttpFetcherTest {

  private static final Duration CONNECT = Duration.ofMillis(500);
  private static final Duration READ = Duration.ofSeconds(2);
  private static final char[] STORE_PASSWORD = "changeit".toCharArray();
  private static final List<String> TRUST_PROPERTIES =
      List.of(
          "javax.net.ssl.trustStore",
          "javax.net.ssl.trustStorePassword",
          "javax.net.ssl.trustStoreType");

  private final ApacheHttpFetcher fetcher = new ApacheHttpFetcher();
  private final AtomicInteger dropped = new AtomicInteger();
  private HttpServer server;
  @TempDir private Path tempDir;

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

  private Response fetchOverTls(GeneralName subjectAltName) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    KeyPair pair = generator.generateKeyPair();
    X500Name subject = new X500Name("CN=kurl test");
    Instant now = Instant.now();
    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now.toEpochMilli()),
            Date.from(now.minus(Duration.ofDays(1))),
            Date.from(now.plus(Duration.ofDays(1))),
            subject,
            pair.getPublic());
    builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(subjectAltName));
    X509Certificate certificate =
        new JcaX509CertificateConverter()
            .getCertificate(
                builder.build(
                    new JcaContentSignerBuilder("SHA256withECDSA").build(pair.getPrivate())));

    KeyStore keys = KeyStore.getInstance("PKCS12");
    keys.load(null, null);
    keys.setKeyEntry("server", pair.getPrivate(), STORE_PASSWORD, new Certificate[] {certificate});
    KeyManagerFactory keyManagers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(keys, STORE_PASSWORD);
    SSLContext serverContext = SSLContext.getInstance("TLS");
    serverContext.init(keyManagers.getKeyManagers(), null, null);

    KeyStore trust = KeyStore.getInstance("PKCS12");
    trust.load(null, null);
    trust.setCertificateEntry("server", certificate);
    Path trustFile = tempDir.resolve("trust-" + now.toEpochMilli() + ".p12");
    try (OutputStream out = Files.newOutputStream(trustFile)) {
      trust.store(out, STORE_PASSWORD);
    }

    HttpsServer tls =
        HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    tls.setHttpsConfigurator(new HttpsConfigurator(serverContext));
    tls.createContext("/ok", exchange -> respond(exchange, 200, "hello"));
    tls.start();
    Map<String, String> previous = new HashMap<>();
    for (String key : TRUST_PROPERTIES) {
      previous.put(key, System.getProperty(key));
    }
    System.setProperty("javax.net.ssl.trustStore", trustFile.toString());
    System.setProperty("javax.net.ssl.trustStorePassword", new String(STORE_PASSWORD));
    System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
    try {
      return fetcher.fetch(
          Request.get(
              pinned("https", tls.getAddress().getPort(), "/ok"), Map.of(), CONNECT, READ, 1_000));
    } finally {
      previous.forEach(
          (key, value) -> {
            if (value == null) {
              System.clearProperty(key);
            } else {
              System.setProperty(key, value);
            }
          });
      tls.stop(0);
    }
  }

  @Test
  void aCertificateForTheOriginalHostnameIsAcceptedOverThePinnedAddress() throws Exception {
    Response response = fetchOverTls(new GeneralName(GeneralName.dNSName, "pinned.invalid"));

    assertThat(response.status()).isEqualTo(200);
    assertThat(text(response)).isEqualTo("hello");
  }

  @Test
  void aCertificateForAnotherHostnameIsRejectedOverThePinnedAddress() {
    assertThatThrownBy(() -> fetchOverTls(new GeneralName(GeneralName.dNSName, "other.invalid")))
        .isInstanceOf(UncheckedIOException.class)
        .cause()
        .isInstanceOf(SSLHandshakeException.class)
        .hasMessageContaining("pinned.invalid");
  }

  @Test
  void aCertificateForThePinnedIpIsRejectedBecauseTheHostnameIsWhatIsChecked() {
    assertThatThrownBy(() -> fetchOverTls(new GeneralName(GeneralName.iPAddress, "127.0.0.1")))
        .isInstanceOf(UncheckedIOException.class)
        .cause()
        .isInstanceOf(SSLHandshakeException.class)
        .hasMessageContaining("pinned.invalid");
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
