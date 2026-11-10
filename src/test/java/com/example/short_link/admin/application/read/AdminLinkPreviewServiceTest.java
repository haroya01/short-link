package com.example.short_link.admin.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.application.read.LinkReportReader;
import com.example.short_link.abuse.application.read.LinkReports;
import com.example.short_link.abuse.application.read.LinkReports.ReasonCount;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.admin.application.helper.PreviewImages;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.net.LoopbackImageFetcher;
import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.link.application.dto.LinkEvidence;
import com.example.short_link.link.application.read.LinkEvidenceReader;
import com.example.short_link.link.domain.ShortCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminLinkPreviewServiceTest {

  private static final ShortCode CODE = new ShortCode("suspect1");
  private static final long LINK_ID = 41L;
  private static final long ADMIN = 7L;
  private static final LinkReports NO_REPORTS = new LinkReports(0, 0, List.of());

  private final LinkEvidenceReader links = mock(LinkEvidenceReader.class);
  private final LinkReportReader reports = mock(LinkReportReader.class);
  private final AuditLogService auditLog = mock(AuditLogService.class);
  private final AtomicInteger asked = new AtomicInteger();
  private HttpServer server;

  @BeforeEach
  void serveImages() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    serve("/og.png", "image/png", PreviewImages.checkerboard(1200, 630, "png"));
    serve("/og.gif", "image/gif", PreviewImages.gif(40, 20));
    serve("/huge.png", "image/png", PreviewImages.blankPng(6000, 5000));
    serve("/broken.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0});
    server.start();
    when(reports.about(LINK_ID)).thenReturn(NO_REPORTS);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void theDefaultPreviewIsTheBlurredOneAndLeavesNoRecord() {
    imageAt(local("/og.png"));

    byte[] shown = service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, false, ADMIN);

    assertThat(shown).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    assertThat(asked).hasValue(1);
    verify(auditLog, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void theClearPreviewRecordsWhoAskedForIt() {
    imageAt(local("/og.png"));

    byte[] shown = service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, true, ADMIN);

    assertThat(shown).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    verify(auditLog)
        .record(AuditAction.LINK_PREVIEW_CLEAR_VIEWED, "link", "suspect1", ADMIN, Map.of());
  }

  @Test
  void aLinkReportedAsSexualContentIsNeverFetchedNotEvenBlurred() {
    imageAt(local("/og.png"));
    when(reports.about(LINK_ID))
        .thenReturn(new LinkReports(2, 1, List.of(new ReasonCount(AbuseReason.SEXUAL, 1))));

    for (boolean clear : List.of(false, true)) {
      assertRefused(
          () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, clear, ADMIN),
          AdminErrorCode.LINK_PREVIEW_WITHHELD,
          null);
    }
    assertThat(asked).hasValue(0);
    verify(auditLog, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void anImageOnAPrivateAddressIsNeverAsked() throws IOException {
    AtomicInteger connections = new AtomicInteger();
    try (ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
      Thread acceptor = Thread.ofVirtual().start(() -> countConnections(listener, connections));
      imageAt("https://127.0.0.1:" + listener.getLocalPort() + "/og.png");

      assertRefused(
          () -> service(new PublicImageFetcher()).preview(CODE, false, ADMIN),
          AdminErrorCode.LINK_PREVIEW_UNAVAILABLE,
          "FETCH_REFUSED");
      acceptor.interrupt();
    }
    assertThat(connections).hasValue(0);
  }

  @Test
  void aResponseThatIsNotJpegOrPngIsRefusedByTheFetcher() {
    imageAt(local("/og.gif"));

    assertRefused(
        () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, true, ADMIN),
        AdminErrorCode.LINK_PREVIEW_UNAVAILABLE,
        "FETCH_REFUSED");
    verify(auditLog, never()).record(any(), any(), any(), any(), any());
  }

  @Test
  void anImageWithTooManyPixelsIsRefusedBeforeDecoding() {
    imageAt(local("/huge.png"));

    assertRefused(
        () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, false, ADMIN),
        AdminErrorCode.LINK_PREVIEW_UNAVAILABLE,
        "TOO_MANY_PIXELS");
  }

  @Test
  void bytesThatDoNotDecodeAreReportedAsSuch() {
    imageAt(local("/broken.png"));

    assertRefused(
        () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, false, ADMIN),
        AdminErrorCode.LINK_PREVIEW_UNAVAILABLE,
        "UNDECODABLE");
  }

  @Test
  void aPageWithoutAnImageAsksNobody() {
    imageAt(null);

    assertRefused(
        () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, false, ADMIN),
        AdminErrorCode.LINK_PREVIEW_UNAVAILABLE,
        "NO_IMAGE");
    assertThat(asked).hasValue(0);
  }

  @Test
  void anUnknownCodeIsNotFound() {
    when(links.image(CODE)).thenReturn(Optional.empty());

    assertRefused(
        () -> service(LoopbackImageFetcher.overPlainHttp()).preview(CODE, false, ADMIN),
        AdminErrorCode.LINK_NOT_FOUND,
        null);
  }

  private AdminLinkPreviewService service(PublicImageFetcher fetcher) {
    return new AdminLinkPreviewService(links, reports, fetcher, auditLog);
  }

  private void imageAt(String url) {
    when(links.image(CODE)).thenReturn(Optional.of(new LinkEvidence.Image(LINK_ID, url)));
  }

  private String local(String path) {
    return "http://og.test:" + server.getAddress().getPort() + path;
  }

  private void serve(String path, String type, byte[] body) {
    server.createContext(
        path,
        exchange -> {
          asked.incrementAndGet();
          exchange.getResponseHeaders().add("Content-Type", type);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
  }

  private static void assertRefused(ThrowingCallable call, AdminErrorCode code, String reason) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            AdminException.class,
            refused -> {
              assertThat(refused.errorCode()).isEqualTo(code);
              assertThat(refused.properties().get("reason")).isEqualTo(reason);
            });
  }

  private static void countConnections(ServerSocket listener, AtomicInteger connections) {
    while (!listener.isClosed()) {
      try (Socket ignored = listener.accept()) {
        connections.incrementAndGet();
      } catch (IOException closed) {
        return;
      }
    }
  }
}
