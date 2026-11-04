package com.example.short_link.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.common.net.PublicImageFetcher.Image;
import com.example.short_link.common.net.PublicImageFetcher.Refused;
import com.example.short_link.common.net.PublicImageFetcher.Result;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class PublicImageFetcherTest {

  private static final int CAP = 64 * 1024;
  private static final byte[] PNG = encode("png");
  private static final byte[] JPEG = encode("jpg");

  private final AtomicInteger hits = new AtomicInteger();
  private final AtomicLong written = new AtomicLong();
  private final Map<String, Map<String, List<String>>> seen = new ConcurrentHashMap<>();
  private final ExecutorService handlers = Executors.newCachedThreadPool();
  private HttpServer server;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(handlers);
    serve("/a.png", exchange -> respond(exchange, 200, "image/png", PNG));
    serve("/a.jpg", exchange -> respond(exchange, 200, "image/jpeg; charset=binary", JPEG));
    serve("/webp", exchange -> respond(exchange, 200, "image/webp", PNG));
    serve("/octet", exchange -> respond(exchange, 200, "application/octet-stream", PNG));
    serve("/jpeg-as-png", exchange -> respond(exchange, 200, "image/png", JPEG));
    serve("/html-as-png", exchange -> respond(exchange, 200, "image/png", "<html>".getBytes()));
    serve("/missing", exchange -> respond(exchange, 404, "image/png", PNG));
    serve("/hop", exchange -> redirect(exchange, "http://media.test:" + port() + "/a.png"));
    serve("/away", exchange -> redirect(exchange, "http://10.0.0.8/a.png"));
    serve("/loop", exchange -> redirect(exchange, "/loop"));
    serve(
        "/cookie",
        exchange -> {
          exchange.getResponseHeaders().add("Set-Cookie", "session=secret; Path=/");
          redirect(exchange, "/echo.png");
        });
    serve("/echo.png", exchange -> respond(exchange, 200, "image/png", PNG));
    serve("/declared-too-long", this::declaredTooLong);
    serve("/endless", this::endless);
    serve("/slow", this::slow);
    serve(
        "/stall",
        exchange -> {
          pause(Duration.ofMillis(1500));
          redirect(exchange, "/stall");
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
    handlers.shutdownNow();
  }

  @Test
  void onlyAnHttpsAddressThatResolvesToAPublicIpIsAsked() {
    PublicImageFetcher production = new PublicImageFetcher();

    for (String url :
        List.of(
            "http://127.0.0.1:" + port() + "/a.png",
            "https://127.0.0.1:" + port() + "/a.png",
            "https://localhost:" + port() + "/a.png",
            "https://10.0.0.8/a.png",
            "https://192.168.0.2/a.png",
            "https://169.254.169.254/latest/meta-data/",
            "https://[::1]:" + port() + "/a.png",
            "https://user:secret@93.184.216.34/a.png",
            "ftp://93.184.216.34/a.png",
            "not a url")) {
      assertThat(production.fetch(url, CAP)).as(url).isInstanceOf(Refused.class);
    }
    assertThat(hits).hasValue(0);
  }

  @Test
  void aPlainHttpAddressOrOneWithCredentialsIsRefusedEvenWhenItResolves() {
    PublicImageFetcher httpsOnly =
        new PublicImageFetcher(
            url ->
                Optional.of(
                    new Resolved(URI.create(url), List.of(InetAddress.getLoopbackAddress()))),
            "https",
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(5));

    assertThat(httpsOnly.fetch(url("/a.png"), CAP))
        .isEqualTo(new Refused("not a public https address"));
    assertThat(fetcher().fetch("http://user:secret@media.test:" + port() + "/a.png", CAP))
        .isEqualTo(new Refused("not a public http address"));
    assertThat(hits).hasValue(0);
  }

  @Test
  void aPngOrJpegServedAsItselfComesBackWholeWithItsType() {
    Image png = (Image) fetcher().fetch(url("/a.png"), CAP);
    Image jpeg = (Image) fetcher().fetch(url("/a.jpg"), CAP);

    assertThat(png.bytes()).isEqualTo(PNG);
    assertThat(png.type()).isEqualTo("image/png");
    assertThat(jpeg.bytes()).isEqualTo(JPEG);
    assertThat(jpeg.type()).isEqualTo("image/jpeg");
  }

  @Test
  void anythingServedAsOtherThanJpegOrPngIsRefused() {
    assertThat(refusal("/webp")).isEqualTo("served as image/webp");
    assertThat(refusal("/octet")).isEqualTo("served as application/octet-stream");
  }

  @Test
  void bytesThatAreNotTheServedTypeAreRefused() {
    assertThat(refusal("/jpeg-as-png")).isEqualTo("bytes are not image/png");
    assertThat(refusal("/html-as-png")).isEqualTo("bytes are not image/png");
  }

  @Test
  void anAnswerOtherThanOkIsRefused() {
    assertThat(refusal("/missing")).isEqualTo("HTTP 404");
  }

  @Test
  @Timeout(10)
  void aDeclaredLengthOverTheCapIsRefusedWithoutWaitingForTheBody() {
    long started = System.nanoTime();

    assertThat(refusal("/declared-too-long")).isEqualTo("larger than 65536 bytes");
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
  }

  @Test
  @Timeout(10)
  void aBodyWithoutALengthIsCutOffAtTheCapAndNotDrained() {
    long started = System.nanoTime();

    assertThat(refusal("/endless")).isEqualTo("larger than 65536 bytes");
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
    assertThat(written.get()).isLessThan(32L * 1024 * 1024);
  }

  @Test
  @Timeout(10)
  void aBodySlowerThanTheDeadlineIsAbandoned() {
    long started = System.nanoTime();

    Result result = fetcher(Duration.ofMillis(400)).fetch(url("/slow"), CAP);

    assertThat(result).isEqualTo(new Refused("slower than the deadline"));
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(3000);
  }

  @Test
  @Timeout(10)
  void theDeadlineCoversEveryRedirectNotOnlyTheLastBody() {
    long started = System.nanoTime();

    Result result = fetcher(Duration.ofMillis(1000)).fetch(url("/stall"), CAP);

    assertThat(result).isEqualTo(new Refused("slower than the deadline"));
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2500);
  }

  @Test
  void aRedirectIsFollowedOnlyToAnAllowedAddressAndOnlyAFewTimes() {
    assertThat(fetcher().fetch(url("/hop"), CAP)).isInstanceOf(Image.class);
    assertThat(refusal("/away")).isEqualTo("not a public http address");
    assertThat(refusal("/loop")).isEqualTo("too many redirects");
  }

  @Test
  void noCookieOrCredentialIsSentAndTheRequestNamesTheTypeItWants() {
    assertThat(fetcher().fetch(url("/cookie"), CAP)).isInstanceOf(Image.class);

    Map<String, List<String>> headers = seen.get("/echo.png");
    assertThat(headers).doesNotContainKeys("Cookie", "Authorization", "Proxy-authorization");
    assertThat(headers.get("Accept")).containsExactly("image/jpeg, image/png");
    assertThat(headers.get("User-agent").getFirst()).startsWith("kurl-media/");
  }

  private String refusal(String path) {
    Result result = fetcher().fetch(url(path), CAP);
    assertThat(result).isInstanceOf(Refused.class);
    return ((Refused) result).reason();
  }

  private PublicImageFetcher fetcher() {
    return fetcher(Duration.ofSeconds(5));
  }

  private PublicImageFetcher fetcher(Duration deadline) {
    return new PublicImageFetcher(
        url -> {
          URI uri = URI.create(url);
          return uri.getHost().endsWith(".test")
              ? Optional.of(new Resolved(uri, List.of(InetAddress.getLoopbackAddress())))
              : Optional.empty();
        },
        "http",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        deadline);
  }

  private String url(String path) {
    return "http://media.test:" + port() + path;
  }

  private int port() {
    return server.getAddress().getPort();
  }

  private void serve(String path, HttpHandler handler) {
    server.createContext(
        path,
        exchange -> {
          hits.incrementAndGet();
          seen.put(path, Map.copyOf(exchange.getRequestHeaders()));
          handler.handle(exchange);
        });
  }

  private static void respond(HttpExchange exchange, int status, String type, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", type);
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static void redirect(HttpExchange exchange, String location) throws IOException {
    exchange.getResponseHeaders().add("Location", location);
    exchange.sendResponseHeaders(302, -1);
    exchange.close();
  }

  private void declaredTooLong(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "image/png");
    exchange.sendResponseHeaders(200, CAP + 1);
    trickle(exchange, Duration.ofSeconds(10));
  }

  private void slow(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "image/png");
    exchange.sendResponseHeaders(200, 0);
    trickle(exchange, Duration.ofSeconds(10));
  }

  private void trickle(HttpExchange exchange, Duration over) {
    long until = System.nanoTime() + over.toNanos();
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(PNG, 0, 8);
      while (System.nanoTime() < until) {
        out.write(new byte[16]);
        out.flush();
        Thread.sleep(100);
      }
    } catch (IOException | InterruptedException closed) {
      return;
    }
  }

  private static void pause(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private void endless(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "image/png");
    exchange.sendResponseHeaders(200, 0);
    byte[] chunk = new byte[64 * 1024];
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(PNG, 0, 8);
      while (written.get() < 512L * 1024 * 1024) {
        out.write(chunk);
        written.addAndGet(chunk.length);
      }
    } catch (IOException closed) {
      return;
    }
  }

  private static byte[] encode(String format) {
    BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
