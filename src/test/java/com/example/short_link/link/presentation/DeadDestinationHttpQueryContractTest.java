package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.observability.RequestMetricsRecorder;
import com.example.short_link.link.health.application.DestinationCheck;
import com.example.short_link.link.health.application.DestinationHealthRecorder;
import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.PendingClick;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DeadDestinationHttpQueryContractTest extends LinkJourneyHttpSupport {

  private static final DestinationCheck NO_HOST =
      new DestinationCheck(DestinationCheck.Outcome.BROKEN, DestinationFailure.NO_HOST, null);
  private static final DestinationCheck NOT_FOUND =
      new DestinationCheck(DestinationCheck.Outcome.BROKEN, DestinationFailure.NOT_FOUND, 404);
  private static final DestinationCheck HEALTHY =
      new DestinationCheck(DestinationCheck.Outcome.HEALTHY, null, 200);
  private static final Map<String, String> KOREAN = Map.of("Accept-Language", "ko");

  @Autowired private DestinationHealthRecorder health;
  @Autowired private ClickBuffer clicks;
  @Autowired private RequestMetricsRecorder requestMetrics;

  @Test
  void aDomainGoneForThreeDaysShowsTheNoticeWithTheSameQueriesAsARedirect() throws Exception {
    String gone = shorten("dead-destination-create-first", "https://pepefc2.onamarudsi/");
    String recent = shorten("dead-destination-create", "https://recently-gone.example/");
    String missing = shorten("dead-destination-create", "https://alive.example/removed");
    String healthy = shorten("dead-destination-create", "https://alive.example/");
    Instant now = Instant.now();
    confirm(gone, "https://pepefc2.onamarudsi/", NO_HOST, now.minus(Duration.ofDays(4)));
    confirm(recent, "https://recently-gone.example/", NO_HOST, now.minus(Duration.ofDays(2)));
    confirm(missing, "https://alive.example/removed", NOT_FOUND, now.minus(Duration.ofDays(9)));

    String notice = page(visit("dead-destination-visit", "/" + gone, KOREAN, 200));
    assertThat(notice)
        .contains("이 링크의 목적지가 지금 열리지 않아요")
        .contains("<p class=\"url\">https://pepefc2.onamarudsi/</p>")
        .contains("href=\"https://pepefc2.onamarudsi/\"")
        .contains("그래도 열기");
    assertThat(
            page(
                visit(
                    "dead-destination-visit-ja", "/" + gone, Map.of("Accept-Language", "ja"), 200)))
        .contains("このリンクの移動先が今は開けません")
        .contains("それでも開く");
    assertThat(location(visit("dead-destination-recent-visit", "/" + recent, KOREAN, 302)))
        .isEqualTo("https://recently-gone.example/");
    assertThat(location(visit("dead-destination-not-found-visit", "/" + missing, KOREAN, 302)))
        .isEqualTo("https://alive.example/removed");
    assertThat(location(visit("dead-destination-healthy-visit", "/" + healthy, KOREAN, 302)))
        .isEqualTo("https://alive.example/");
    String preview =
        page(
            visit(
                "dead-destination-preview-bot",
                "/" + gone,
                Map.of("User-Agent", "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)"),
                200));
    assertThat(preview).contains("og:").doesNotContain("그래도 열기");

    record(gone, "https://pepefc2.onamarudsi/", HEALTHY, now);
    assertThat(location(visit("dead-destination-recovered-visit", "/" + gone, KOREAN, 302)))
        .isEqualTo("https://pepefc2.onamarudsi/");
    confirm(healthy, "https://alive.example/", NO_HOST, now.minus(Duration.ofDays(5)));
    assertThat(page(visit("dead-destination-newly-gone-visit", "/" + healthy, KOREAN, 200)))
        .contains("https://alive.example/");

    long goneId = linkId(gone);
    List<PendingClick> recorded =
        clicks.drain(1_000).stream()
            .filter(click -> click.ctx().linkId().value() == goneId)
            .toList();
    assertThat(recorded).filteredOn(click -> click.forcedBotName() == null).hasSize(3);
    assertThat(recorded).filteredOn(click -> click.forcedBotName() != null).hasSize(1);
    requestMetrics.flush();
    assertThat(
            number(
                "SELECT COUNT(*) FROM request_metrics WHERE short_code = ?"
                    + " AND outcome = 'dead_destination'",
                gone))
        .isEqualTo(2);
    assertThat(
            number(
                "SELECT COUNT(*) FROM request_metrics WHERE short_code = ?"
                    + " AND outcome = 'dead_destination'",
                healthy))
        .isEqualTo(1);
  }

  private String shorten(String id, String url) throws Exception {
    return request(id, owner, "POST", "/api/v1/links", Map.of("url", url), 201)
        .path("shortCode")
        .asText();
  }

  private void confirm(String code, String url, DestinationCheck check, Instant since) {
    record(code, url, check, since.minus(Duration.ofHours(20)));
    record(code, url, check, since);
    awaitAsyncWork();
  }

  private void record(String code, String url, DestinationCheck check, Instant at) {
    health.record(new DueDestination(linkId(code), code, owner.id(), url, null), check, at);
  }

  private static String page(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  private static String location(HttpResponse<byte[]> response) {
    return response.headers().firstValue("Location").orElse(null);
  }
}
