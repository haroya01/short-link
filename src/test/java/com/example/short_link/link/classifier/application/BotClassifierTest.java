package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.UserAgentInfo;
import org.junit.jupiter.api.Test;

class BotClassifierTest {

  private static final String IP = "203.0.113.7";
  private static final UserAgentInfo HUMAN_AGENT = UserAgentInfo.unknown();
  private static final UserAgentInfo CRAWLER =
      new UserAgentInfo("robot", "unknown", "unknown", true, "Googlebot");
  private static final AsnResolver.AsnInfo HOME = AsnResolver.AsnInfo.empty();
  private static final AsnResolver.AsnInfo HOSTING =
      new AsnResolver.AsnInfo(16509, "Amazon.com, Inc.", true, false);

  private final BotHeuristic burst = mock(BotHeuristic.class);
  private final BotClassifier classifier = new BotClassifier(burst);

  @Test
  void aDeclaredCrawlerDoesNotCountTowardsTheIpBurst() {
    BotClassifier.Verdict verdict = classifier.classify(CRAWLER, HOSTING, IP);

    assertThat(verdict).isEqualTo(BotClassifier.Verdict.bot("Googlebot"));
    verify(burst, never()).isSuspectBurst(any());
  }

  @Test
  void aBurstIsReportedBeforeTheHostingNetwork() {
    when(burst.isSuspectBurst(IP)).thenReturn(true);

    assertThat(classifier.classify(HUMAN_AGENT, HOSTING, IP))
        .isEqualTo(BotClassifier.Verdict.bot(BotHeuristic.SUSPECT_LABEL));
  }

  @Test
  void aHostingNetworkIsNamedByItsOrganisation() {
    assertThat(classifier.classify(HUMAN_AGENT, HOSTING, IP))
        .isEqualTo(BotClassifier.Verdict.bot("datacenter:Amazon.com, Inc."));
    assertThat(
            classifier.classify(HUMAN_AGENT, new AsnResolver.AsnInfo(64500, null, true, false), IP))
        .isEqualTo(BotClassifier.Verdict.bot("datacenter:unknown"));
  }

  @Test
  void anOrdinaryVisitorIsHuman() {
    assertThat(classifier.classify(HUMAN_AGENT, HOME, IP)).isEqualTo(BotClassifier.Verdict.HUMAN);
    verify(burst).isSuspectBurst(IP);
  }
}
