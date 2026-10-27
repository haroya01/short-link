package com.example.short_link.link.redirect.application;

import static com.example.short_link.link.application.dto.CachedLinkFixture.link;
import static com.example.short_link.link.application.dto.CachedLinkFixture.variant;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RedirectDeciderTest {

  private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
  private static final Long OWNER = 7L;

  private static final class FakeFacts implements RedirectDecider.Facts {
    Long hostOwner;
    Set<String> blockedUrls = Set.of();
    String crawler;
    final List<String> asked = new ArrayList<>();

    @Override
    public Long hostOwner() {
      asked.add("hostOwner");
      return hostOwner;
    }

    @Override
    public boolean blocked(String url) {
      asked.add("blocked " + url);
      return blockedUrls.contains(url);
    }

    @Override
    public String crawlerName() {
      asked.add("crawler");
      return crawler;
    }

    @Override
    public Instant now() {
      asked.add("now");
      return NOW;
    }
  }

  private final FakeFacts facts = new FakeFacts();

  private static CachedLink open() {
    return link(new LinkId(1L), "https://dest.example").userId(OWNER).build();
  }

  private static CachedLink opensLater(boolean password, Integer maxViews) {
    return link(new LinkId(1L), "https://dest.example")
        .userId(OWNER)
        .passwordRequired(password)
        .maxViews(maxViews)
        .visitOptions(new CachedLink.VisitOptions(false, null, NOW.plusSeconds(60)))
        .build();
  }

  @Test
  void aPlainVisitAsksEveryFactOnceInOrderAndFollowsTheFlow() {
    CachedLink link =
        link(new LinkId(1L), "https://dest.example")
            .userId(OWNER)
            .variants(
                List.of(
                    variant(1L, "https://a.example", 1, true, null),
                    variant(2L, "https://off.example", 1, false, null)))
            .build();

    assertThat(RedirectDecider.decide(link, facts)).isEqualTo(new RedirectDecision.Visit());
    assertThat(facts.asked)
        .containsExactly(
            "hostOwner",
            "blocked https://dest.example",
            "blocked https://a.example",
            "now",
            "crawler");
  }

  @Test
  void aLinkOnSomeoneElsesDomainIsNotFoundBeforeAnythingElseIsAsked() {
    facts.hostOwner = 99L;
    facts.blockedUrls = Set.of("https://dest.example");

    assertThat(RedirectDecider.decide(open(), facts))
        .isEqualTo(new RedirectDecision.NotOnThisHost());
    assertThat(facts.asked).containsExactly("hostOwner");
  }

  @Test
  void theOwnersOwnDomainAndTheMainDomainBothServeTheLink() {
    facts.hostOwner = OWNER;
    assertThat(RedirectDecider.decide(open(), facts)).isEqualTo(new RedirectDecision.Visit());
  }

  @Test
  void anyEnabledBlockedDestinationBlocksEvenAPreview() {
    facts.crawler = "Slackbot";
    facts.blockedUrls = Set.of("https://a.example");
    CachedLink link =
        link(new LinkId(1L), "https://dest.example")
            .userId(OWNER)
            .variants(List.of(variant(1L, "https://a.example", 1, true, null)))
            .build();

    assertThat(RedirectDecider.decide(link, facts))
        .isEqualTo(new RedirectDecision.DestinationBlocked());
  }

  @Test
  void aBlockedButDisabledVariantDoesNotBlock() {
    facts.blockedUrls = Set.of("https://off.example");
    CachedLink link =
        link(new LinkId(1L), "https://dest.example")
            .userId(OWNER)
            .variants(List.of(variant(2L, "https://off.example", 1, false, null)))
            .build();

    assertThat(RedirectDecider.decide(link, facts)).isEqualTo(new RedirectDecision.Visit());
    assertThat(facts.asked).doesNotContain("blocked https://off.example");
  }

  @Test
  void beforeOpeningAPersonWaitsAndACrawlerSeesACardWithoutTheDestination() {
    assertThat(RedirectDecider.decide(opensLater(false, null), facts))
        .isEqualTo(new RedirectDecision.NotYetOpen(NOW.plusSeconds(60)));

    facts.crawler = "Slackbot";
    assertThat(RedirectDecider.decide(opensLater(false, null), facts))
        .isEqualTo(new RedirectDecision.Preview(PreviewScope.WITHOUT_DESTINATION_URL, "Slackbot"));
    assertThat(RedirectDecider.decide(opensLater(true, null), facts))
        .isEqualTo(new RedirectDecision.Preview(PreviewScope.KURL_CARD_ONLY, "Slackbot"));
    assertThat(RedirectDecider.decide(opensLater(false, 3), facts))
        .isEqualTo(new RedirectDecision.Preview(PreviewScope.KURL_CARD_ONLY, "Slackbot"));
  }

  @Test
  void aPasswordIsAskedBeforeTheCrawlerCheckSoASpoofedAgentSeesNothing() {
    facts.crawler = "Slackbot";
    CachedLink link =
        link(new LinkId(1L), "https://dest.example").userId(OWNER).passwordRequired(true).build();

    assertThat(RedirectDecider.decide(link, facts))
        .isEqualTo(new RedirectDecision.PasswordPrompt());
    assertThat(facts.asked).doesNotContain("crawler");
  }

  @Test
  void aCrawlerSeesTheDestinationUnlessTheLinkLimitsItsVisitors() {
    facts.crawler = "Slackbot";

    assertThat(RedirectDecider.decide(open(), facts))
        .isEqualTo(new RedirectDecision.Preview(PreviewScope.DESTINATION, "Slackbot"));
    CachedLink limited =
        link(new LinkId(1L), "https://dest.example").userId(OWNER).blockedCountries("KR").build();
    assertThat(RedirectDecider.decide(limited, facts))
        .isEqualTo(new RedirectDecision.LimitedCrawler("Slackbot"));
  }

  private static LinkEntity expired(String redirectUrl, String message) {
    LinkEntity entity = new LinkEntity("https://dest.example", "abc1234", OWNER, NOW);
    entity.changeExpiredRedirectUrl(redirectUrl);
    entity.updateExpiredMessage(message);
    return entity;
  }

  @Test
  void anExpiredLinkWithANextAddressSendsVisitorsOnWhereAllowed() {
    assertThat(RedirectDecider.decideExpired(expired("https://next.example", "bye"), facts))
        .isEqualTo(new RedirectDecision.AfterExpiry("https://next.example"));
    assertThat(facts.asked).containsExactly("hostOwner", "blocked https://next.example");

    facts.blockedUrls = Set.of("https://next.example");
    assertThat(RedirectDecider.decideExpired(expired("https://next.example", null), facts))
        .isEqualTo(new RedirectDecision.DestinationBlocked());

    facts.hostOwner = 99L;
    assertThat(RedirectDecider.decideExpired(expired("https://next.example", null), facts))
        .isEqualTo(new RedirectDecision.NotOnThisHost());
  }

  @Test
  void anExpiredLinkWithOnlyAMessageShowsItAndWithNeitherFallsThrough() {
    assertThat(RedirectDecider.decideExpired(expired(null, "bye"), facts))
        .isEqualTo(new RedirectDecision.ExpiredMessage("bye"));
    assertThat(RedirectDecider.decideExpired(expired(null, null), facts))
        .isEqualTo(new RedirectDecision.Unhandled());
    assertThat(RedirectDecider.decideExpired(null, facts))
        .isEqualTo(new RedirectDecision.Unhandled());
    assertThat(facts.asked).isEmpty();
  }
}
