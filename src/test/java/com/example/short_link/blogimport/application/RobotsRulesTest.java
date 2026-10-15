package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsRulesTest {

  private static final String AGENT = "kurl-importer";

  // rss.blog.naver.com/robots.txt as served on 2026-10-16, abridged.
  private static final String NAVER_RSS =
      """
      # Block everything for every crawler
      User-agent: *
      Disallow: /

      User-agent: GPTBot
      Disallow: /

      # Naver's Yeti
      User-agent: Yeti
      Disallow: /
      """;

  // brunch.co.kr/robots.txt, abridged: no "*" group.
  private static final String BRUNCH =
      """
      User-agent: GPTBot
      User-agent: ClaudeBot
      Disallow: /

      User-agent: Googlebot
      User-agent: Yeti
      Disallow: /write
      Disallow: /*/stats$
      """;

  // notice.tistory.com/robots.txt.
  private static final String TISTORY =
      """
      User-agent: *
      Disallow: /guestbook
      Disallow: /manage
      Disallow: /search

      User-agent: bingbot
      Crawl-delay: 20
      """;

  @Test
  void naversRssHostShutsEveryoneOutUnlessOnlyNamedGroupsCount() {
    RobotsRules rules = RobotsRules.parse(NAVER_RSS);

    assertThat(rules.allows(AGENT, "/naverofficial.xml", false)).isFalse();
    assertThat(rules.allows(AGENT, "/naverofficial.xml", true)).isTrue();
  }

  @Test
  void brunchAndTistoryLeaveTheirFeedsOpen() {
    assertThat(RobotsRules.parse(BRUNCH).allows(AGENT, "/rss/@@2xr", false)).isTrue();
    assertThat(RobotsRules.parse(BRUNCH).allows(AGENT, "/@brunch", false)).isTrue();
    assertThat(RobotsRules.parse(TISTORY).allows(AGENT, "/rss", false)).isTrue();
    assertThat(RobotsRules.parse(TISTORY).allows(AGENT, "/manage/settings", false)).isFalse();
    assertThat(RobotsRules.parse("").allows(AGENT, "/rss", false)).isTrue();
    assertThat(RobotsRules.parse(null).allows(AGENT, "/rss", false)).isTrue();
  }

  @Test
  void aGroupNamingUsWinsOverTheWildcardAndCombinesWithItsTwin() {
    RobotsRules rules =
        RobotsRules.parse(
            """
            User-agent: *
            Disallow: /

            User-agent: Kurl-Importer
            Disallow: /private
            User-agent: other

            User-agent: kurl-importer
            Disallow: /drafts
            """);

    assertThat(rules.allows(AGENT, "/rss", false)).isTrue();
    assertThat(rules.allows(AGENT, "/private/x", false)).isFalse();
    assertThat(rules.allows(AGENT, "/drafts/1", true)).isFalse();
  }

  @Test
  void theLongestMatchWinsAndAllowWinsATie() {
    RobotsRules rules =
        RobotsRules.parse(
            """
            user-agent: *   # comment
            disallow: /feeds
            allow: /feeds/public
            disallow: /same
            allow: /same
            disallow:
            """);

    assertThat(rules.allows(AGENT, "/feeds/private", false)).isFalse();
    assertThat(rules.allows(AGENT, "/feeds/public/rss", false)).isTrue();
    assertThat(rules.allows(AGENT, "/same", false)).isTrue();
    assertThat(rules.allows(AGENT, "/other", false)).isTrue();
    assertThat(rules.allows(AGENT, "", false)).isTrue();
  }

  @Test
  void wildcardsAndEndAnchorsMatchLikeTheRfc() {
    assertThat(RobotsRules.matches("/*/stats$", "/@brunch/stats")).isTrue();
    assertThat(RobotsRules.matches("/*/stats$", "/@brunch/stats/more")).isFalse();
    assertThat(RobotsRules.matches("/*?timestamp=*", "/a?timestamp=1")).isTrue();
    assertThat(RobotsRules.matches("/rss", "/rss/@@2xr")).isTrue();
    assertThat(RobotsRules.matches("/rss$", "/rss")).isTrue();
    assertThat(RobotsRules.matches("/rss$", "/rs")).isFalse();
  }

  @Test
  void rulesBeforeAnyUserAgentAndOtherFieldsAreIgnored() {
    RobotsRules rules =
        RobotsRules.parse("Disallow: /\nSitemap: https://x.example/s.xml\nnot a field\n");

    assertThat(rules.allows(AGENT, "/rss", false)).isTrue();
  }
}
