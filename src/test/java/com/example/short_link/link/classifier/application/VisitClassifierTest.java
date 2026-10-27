package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.analytics.Utm;
import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisitClassifierTest {

  private static final UserAgentInfo PHONE =
      new UserAgentInfo("mobile", "iOS", "Safari", false, null);

  private final UserAgentClassifier userAgents = mock(UserAgentClassifier.class);
  private final GeoIpResolver geo = mock(GeoIpResolver.class);
  private final AsnResolver asns = mock(AsnResolver.class);
  private final BotHeuristic heuristic = mock(BotHeuristic.class);
  private final VisitClassifier classifier =
      new VisitClassifier(userAgents, geo, asns, new BotClassifier(heuristic));

  private static VisitRequest request(boolean gpc) {
    return new VisitRequest(
        "https://t.co/x?ref=1",
        "Mozilla/5.0",
        "203.0.113.7",
        "ko-KR,ko;q=0.9",
        "x",
        Utm.none(),
        gpc,
        null);
  }

  @BeforeEach
  void lookups() {
    when(userAgents.classify("Mozilla/5.0")).thenReturn(PHONE);
    when(geo.resolve("203.0.113.7")).thenReturn(new GeoLocation("KR", "Seoul", "Seoul"));
    when(asns.resolve("203.0.113.7"))
        .thenReturn(new AsnResolver.AsnInfo(4766, "Korea Telecom", false, false));
  }

  @Test
  void aVisitIsClassifiedOnceForEveryRecorder() {
    ClassifiedVisit visit = classifier.classify(request(false), 42L);

    assertThat(visit.device()).isEqualTo(PHONE);
    assertThat(visit.bot().isBot()).isFalse();
    assertThat(visit.geo().countryCode()).isEqualTo("KR");
    assertThat(visit.asn().asn()).isEqualTo(4766);
    assertThat(visit.referrer())
        .isEqualTo(
            new ClassifiedVisit.Referrer(
                ReferrerNormalizer.normalize("https://t.co/x?ref=1"),
                ReferrerNormalizer.hostOf("https://t.co/x?ref=1")));
    assertThat(visit.maskedIp()).isEqualTo(IpMasker.mask("203.0.113.7"));
    assertThat(visit.language()).isEqualTo(LanguageExtractor.extract("ko-KR,ko;q=0.9"));
    assertThat(visit.sourceChannel()).isEqualTo(SourceChannelNormalizer.normalize("x"));
    assertThat(visit.visitorHash())
        .isEqualTo(VisitorHasher.hash(42L, "203.0.113.7", "Mozilla/5.0"));
  }

  @Test
  void aVisitorAskingNotToBeTrackedGetsNoVisitorHash() {
    assertThat(classifier.classify(request(true), 42L).visitorHash()).isNull();
  }

  @Test
  void aKnownBotSkipsTheBotHeuristic() {
    ClassifiedVisit visit = classifier.classifyKnownBot(request(false), 42L, "prefetch");

    assertThat(visit.bot()).isEqualTo(BotClassifier.Verdict.bot("prefetch"));
    assertThat(visit.asn().asn()).isEqualTo(4766);
    verifyNoInteractions(heuristic);
  }

  @Test
  void theHashIsScopedToWhatWasVisited() {
    assertThat(classifier.classify(request(false), 42L).visitorHash())
        .isNotEqualTo(classifier.classify(request(false), 43L).visitorHash());
  }
}
