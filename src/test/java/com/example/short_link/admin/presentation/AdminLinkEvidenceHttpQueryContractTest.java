package com.example.short_link.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.helper.PreviewImages;
import com.example.short_link.common.audit.AddressChange;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class AdminLinkEvidenceHttpQueryContractTest extends OperationalHttpJourneySupport {

  private static final String OG_IMAGE = "https://cdn.prize.example/og.png";

  @MockitoBean private PublicImageFetcher images;
  @Autowired private LinkRepository links;
  @Autowired private AuditLogService auditLog;
  @LocalServerPort private int port;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @Test
  void anOperatorJudgesASuspiciousLinkWithoutOpeningIt() throws Exception {
    Actor admin = actor("evidence-admin", true);
    Actor owner = actor("evidence-owner", false);
    Actor member = actor("evidence-member", false);
    String code = "suspect1";
    long linkId =
        transactions.execute(
            transaction -> {
              links.save(new LinkEntity("https://old.example", "ownother1", owner.id(), null));
              long off =
                  links
                      .save(new LinkEntity("https://gone.example", "ownoff1", owner.id(), null))
                      .getId();
              jdbc.update(
                  "INSERT INTO link_moderation (link_id, reason, disabled_by, disabled_at,"
                      + " created_at, updated_at) VALUES (?, 'ADMIN', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),"
                      + " UTC_TIMESTAMP(6))",
                  off);
              return links
                  .save(
                      new LinkEntity(
                          "https://prize.example/claim?token=t1",
                          new ShortCode(code),
                          owner.id(),
                          null))
                  .getId();
            });
    jdbc.update(
        "UPDATE link SET og_title = 'Claim your prize', og_description = 'Enter your card',"
            + " og_image = ?, og_fetch_status = 'OK', og_fetched_at = UTC_TIMESTAMP(6) WHERE id = ?",
        OG_IMAGE,
        linkId);
    jdbc.update(
        "INSERT INTO link_destination (link_id, url, weight, label, enabled, country_code,"
            + " device_class, os, created_at) VALUES (?, 'https://ios.prize.example/a', 3, 'B',"
            + " TRUE, 'KR', 'mobile', 'ios', UTC_TIMESTAMP(6))",
        linkId);
    auditLog.record(
        AuditAction.LINK_UPDATED,
        "link",
        code,
        owner.id(),
        AddressChange.between("https://safe.example/menu", "https://prize.example/claim"));
    jdbc.update(
        "INSERT INTO abuse_report (reporter_user_id, subject_type, subject_id, reason_code,"
            + " status, created_at) VALUES (?, 'LINK', ?, 'PHISHING', 'OPEN', UTC_TIMESTAMP(6))",
        member.id(),
        linkId);
    when(images.fetch(anyString(), anyInt()))
        .thenReturn(
            new PublicImageFetcher.Image(
                PreviewImages.checkerboard(1200, 630, "png"), PublicImageFetcher.PNG));
    String detail = "/api/v1/admin/links/" + code;
    String preview = detail + "/preview-image";

    step("admin-evidence-denies-member", "GET", detail, member, null, 403);
    JsonNode evidence =
        step("admin-evidence-detail", "GET", detail, admin, null, 200).path("evidence");
    assertThat(evidence.path("page").path("title").asText()).isEqualTo("Claim your prize");
    assertThat(evidence.path("page").path("hasImage").asBoolean()).isTrue();
    assertThat(evidence.path("page").path("imageWithheld").asBoolean()).isFalse();
    assertThat(evidence.toString()).doesNotContain(OG_IMAGE).doesNotContain("token=t1");
    assertThat(evidence.path("destinations").get(0).path("host").asText())
        .isEqualTo("prize.example");
    assertThat(evidence.path("destinations").get(1).path("host").asText())
        .isEqualTo("ios.prize.example");
    assertThat(evidence.path("destinations").size()).isEqualTo(2);
    assertThat(evidence.path("history").path("urlChanges").asLong()).isEqualTo(1);
    assertThat(evidence.path("history").path("latest").path("fromHost").asText())
        .isEqualTo("safe.example");
    assertThat(evidence.path("creator").path("links").asLong()).isEqualTo(3);
    assertThat(evidence.path("creator").path("disabledLinks").asLong()).isEqualTo(1);
    assertThat(evidence.path("creator").path("status").asText()).isEqualTo("ACTIVE");
    assertThat(evidence.path("reports").path("open").asLong()).isEqualTo(1);

    step("admin-preview-denies-member", "GET", preview, member, null, 403);
    HttpResponse<byte[]> blurred = image("admin-preview-blurred", preview, admin);
    assertThat(blurred.headers().firstValue("Content-Type")).contains("image/jpeg");
    assertThat(blurred.headers().firstValue("Cache-Control")).contains("no-store");
    BufferedImage shown = ImageIO.read(new ByteArrayInputStream(blurred.body()));
    assertThat(shown.getWidth()).isEqualTo(320);
    assertThat(clearViews(admin)).isZero();

    HttpResponse<byte[]> clear = image("admin-preview-clear", preview + "?clear=true", admin);
    assertThat(clear.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(clearViews(admin)).isEqualTo(1);

    jdbc.update(
        "INSERT INTO abuse_report (reporter_user_id, subject_type, subject_id, reason_code,"
            + " status, created_at) VALUES (NULL, 'LINK', ?, 'SEXUAL', 'OPEN', UTC_TIMESTAMP(6))",
        linkId);
    assertThat(
            step("admin-preview-withheld", "GET", preview + "?clear=true", admin, null, 451)
                .path("code")
                .asText())
        .isEqualTo("LINK_PREVIEW_WITHHELD");
    assertThat(
            step("admin-evidence-withheld", "GET", detail, admin, null, 200)
                .path("evidence")
                .path("page")
                .path("imageWithheld")
                .asBoolean())
        .isTrue();
    verify(images, times(2)).fetch(OG_IMAGE, 5 * 1024 * 1024);
    assertThat(clearViews(admin)).isEqualTo(1);
  }

  private long clearViews(Actor admin) {
    return count(
        "audit_log",
        "action = ? AND actor_user_id = ? AND target_id = 'suspect1'",
        AuditAction.LINK_PREVIEW_CLEAR_VIEWED.name(),
        admin.id());
  }

  private HttpResponse<byte[]> image(String id, String path, Actor actor) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("X-Query-Contract-ID", id)
            .header("User-Agent", "Mozilla/5.0 Chrome/130.0.0.0 Safari/537.36")
            .header("Authorization", "Bearer " + actor.token())
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    var result =
        contracts.capture(id, () -> http.send(request, HttpResponse.BodyHandlers.ofByteArray()));
    contracts.verify(result);
    assertThat(result.response().statusCode()).as(path).isEqualTo(200);
    return result.response();
  }
}
