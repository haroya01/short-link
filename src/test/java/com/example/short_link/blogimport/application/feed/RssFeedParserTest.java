package com.example.short_link.blogimport.application.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RssFeedParserTest {

  private static String fixture(String name) throws Exception {
    return Files.readString(Path.of("src/test/resources/blog-import/" + name));
  }

  @Test
  void naverGivesTheBlogDescriptionAndCleanGuids() throws Exception {
    FeedDocument doc = RssFeedParser.parse(fixture("naver.xml")).orElseThrow();

    assertThat(doc.title()).isEqualTo("네이버 공식블로그");
    assertThat(doc.description()).isEqualTo("차곡차곡 쌓인 네이버 소식의 모든 것, 네이버 다이어리입니다.");
    assertThat(doc.language()).isEqualTo("ko");
    assertThat(doc.entries()).hasSize(3);
    FeedEntry first = doc.entries().get(0);
    assertThat(first.title()).isEqualTo("월 최대 40만 원! 라운지 서포터즈 참여하세요");
    assertThat(first.link()).contains("?fromRss=true");
    assertThat(first.guid()).isEqualTo("https://blog.naver.com/naverofficial/224429356463");
    assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-10-02T04:53:56Z"));
    assertThat(first.html()).contains("<img src=\"https://blogthumb.pstatic.net/");
    assertThat(doc.entries().get(2).tags()).contains("개천절", "한연진작가");
  }

  @Test
  void tistoryEscapedHtmlArrivesAsMarkup() throws Exception {
    FeedDocument doc = RssFeedParser.parse(fixture("tistory.xml")).orElseThrow();

    assertThat(doc.title()).isEqualTo("TISTORY");
    assertThat(doc.entries()).hasSize(4);
    assertThat(doc.entries().get(0).html()).startsWith("<p");
    assertThat(doc.entries().get(0).link()).isEqualTo("https://notice.tistory.com/2702");
  }

  @Test
  void velogKeepsCodeBlockNewlinesFromCdata() throws Exception {
    FeedDocument doc = RssFeedParser.parse(fixture("velog.xml")).orElseThrow();

    assertThat(doc.language()).isEmpty();
    assertThat(doc.entries().get(1).html())
        .contains("<pre><code class=\"language-jsx\">import { json, LoaderFunction }")
        .contains("\n");
    assertThat(doc.entries().get(1).publishedAt()).isEqualTo(Instant.parse("2022-10-23T14:40:32Z"));
  }

  @Test
  void brunchTitlesLoseTheirDoubleEscapedEntities() throws Exception {
    FeedDocument doc = RssFeedParser.parse(fixture("brunch.xml")).orElseThrow();

    assertThat(doc.entries().get(1).title()).contains("문화체육관광부·한국문화예술위원회");
  }

  @Test
  void contentEncodedWinsOverTheDescription() {
    String xml =
        "<rss xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel><title>t</title>"
            + "<item><title>a</title><link>https://x.example/1</link>"
            + "<description>short</description>"
            + "<content:encoded><![CDATA[<p>full</p>]]></content:encoded></item></channel></rss>";

    assertThat(RssFeedParser.parse(xml).orElseThrow().entries().get(0).html())
        .isEqualTo("<p>full</p>");
  }

  @Test
  void anEmptyChannelIsEmpty() {
    FeedDocument doc =
        RssFeedParser.parse("<rss><channel><title/><link/><description/></channel></rss>")
            .orElseThrow();

    assertThat(doc.isEmpty()).isTrue();
  }

  @Test
  void documentsThatAreNotRssAreRejected() {
    assertThat(RssFeedParser.parse("<html><body>404</body></html>")).isEmpty();
    assertThat(RssFeedParser.parse("")).isEmpty();
    assertThat(RssFeedParser.parse(null)).isEmpty();
  }

  @Test
  void externalEntitiesAreNeverResolved() {
    String xml =
        "<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<rss><channel><title>&xxe;</title><description>d</description></channel></rss>";

    FeedDocument doc = RssFeedParser.parse(xml).orElseThrow();

    assertThat(doc.title()).doesNotContain("root:");
  }

  @Test
  void entriesStopAtFifty() {
    StringBuilder xml = new StringBuilder("<rss><channel><title>t</title>");
    for (int i = 0; i < 60; i++) {
      xml.append("<item><title>p").append(i).append("</title><link>https://x.example/").append(i);
      xml.append("</link></item>");
    }
    xml.append("</channel></rss>");

    assertThat(RssFeedParser.parse(xml.toString()).orElseThrow().entries()).hasSize(50);
  }

  @Test
  void unreadableDatesAreNull() {
    assertThat(RssFeedParser.date("yesterday")).isNull();
    assertThat(RssFeedParser.date(" ")).isNull();
    assertThat(RssFeedParser.date("Wed, 30 Sep 2026 05:16:48 GMT"))
        .isEqualTo(Instant.parse("2026-09-30T05:16:48Z"));
  }
}
