package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.federation.domain.topics.TopicSighting;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TopicAnswersTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static JsonNode rows(String... rows) {
    return JSON.readTree(TopicFixtures.rows(rows));
  }

  private static String tag(String name, int... accountsNewestFirst) {
    return TopicFixtures.tagRow(name, accountsNewestFirst);
  }

  private static String link(String url, String title, String language, int... accounts) {
    return TopicFixtures.linkRow(url, title, language, accounts);
  }

  @Test
  void peopleAreTheAccountsOfTheNewestTwoDays() {
    List<TopicSighting> tags =
        TopicAnswers.tags(
            rows(tag("harbor", 4, 3, 100, 100), tag("lantern", 5), tag("empty")), "a.example");

    assertThat(tags).extracting(TopicSighting::people).containsExactly(7, 5, 0);
    assertThat(tags).extracting(TopicSighting::place).containsExactly(0, 1, 2);
    assertThat(tags).extracting(TopicSighting::host).containsOnly("a.example");
  }

  @Test
  void aCountThatIsNotANumberOrIsNegativeCountsNobody() {
    JsonNode history =
        JSON.readTree("[{\"accounts\":\"many\"},{\"accounts\":\"-4\"},{\"accounts\":\"9\"}]");
    assertThat(TopicAnswers.people(history)).isZero();
    assertThat(TopicAnswers.people(JSON.readTree("[{\"accounts\":6},{\"uses\":\"2\"}]")))
        .isEqualTo(6);
    assertThat(TopicAnswers.people(JSON.readTree("{}").path("history"))).isZero();
  }

  @Test
  void aTagKeepsItsPlaceInTheAnswerAndReadsItsLanguageFromItsScript() {
    List<TopicSighting> tags =
        TopicAnswers.tags(
            rows(
                tag(" ", 9, 9),
                "\"not a row\"",
                tag("소풍", 2, 2),
                tag("x".repeat(101), 9, 9),
                tag("#やまびこ", 1, 2),
                tag("y".repeat(100), 3, 0)),
            "a.example");

    assertThat(tags).extracting(TopicSighting::tag).containsExactly("소풍", "やまびこ", "y".repeat(100));
    assertThat(tags).extracting(TopicSighting::place).containsExactly(2, 4, 5);
    assertThat(tags).extracting(TopicSighting::language).containsExactly("ko", "ja", null);
  }

  @Test
  void aLinkNeedsAnHttpsAddressAndATitle() {
    List<TopicSighting> links =
        TopicAnswers.links(
            rows(
                link("http://news.example/plain", "Plain", "en", 5, 5),
                link("https://news.example/untitled", "  ", "en", 5, 5),
                link("https://news.example/a", "  The bridge\n reopens ", "en-US", 2, 4),
                link("https://news.example/" + "p".repeat(600), "Long address", "en", 5, 5)),
            "a.example");

    assertThat(links).hasSize(1);
    TopicSighting link = links.getFirst();
    assertThat(link.place()).isEqualTo(2);
    assertThat(link.people()).isEqualTo(6);
    assertThat(link.language()).isEqualTo("en");
    assertThat(link.tag()).isNull();
    assertThat(link.article().url()).isEqualTo("https://news.example/a");
    assertThat(link.article().title()).isEqualTo("The bridge reopens");
    assertThat(link.article().source()).isEqualTo("Example Daily");
    assertThat(link.article().image()).isEqualTo("https://img.example/a.png");
  }

  @Test
  void aLinkTitleIsCutAsACardTitleIsAndAPlainImageIsDropped() {
    String row =
        link("https://news.example/a", "t".repeat(400), "en", 3, 3)
            .replace("https://img.example/a.png", "http://img.example/a.png");

    TopicSighting link = TopicAnswers.links(rows(row), "a.example").getFirst();

    assertThat(link.article().title()).hasSize(300).endsWith("…");
    assertThat(link.article().image()).isNull();
  }

  @Test
  void aLinkTitleLosesTheSourceNameItsSiteAppended() {
    List<TopicSighting> links =
        TopicAnswers.links(
            rows(
                link(
                    "https://news.example/a",
                    " The bridge\n reopens  |  EXAMPLE daily ",
                    "en",
                    3,
                    3),
                link("https://news.example/b", "다리가 다시 열린다 - Example Daily", null, 3, 3),
                link("https://news.example/c", "Example Daily", "en", 3, 3),
                link("https://news.example/d", "The ferry | Another Paper", "en", 3, 3)),
            "a.example");

    assertThat(links)
        .extracting(sighting -> sighting.article().title())
        .containsExactly(
            "The bridge reopens", "다리가 다시 열린다", "Example Daily", "The ferry | Another Paper");
    assertThat(links).extracting(TopicSighting::language).containsExactly("en", "ko", "en", "en");
    assertThat(links.getFirst().article().source()).isEqualTo("Example Daily");
  }

  @Test
  void thisServersOwnLinkHasNoSourceSoItsTitleIsKeptWhole() {
    List<TopicSighting> links =
        TopicAnswers.ownLinks(
            List.of(
                new NoteTopicReader.Link(
                    "https://news.example/a", "The bridge reopens | Example Daily", null, 3)),
            "kurl.me");

    assertThat(links.getFirst().article().title()).isEqualTo("The bridge reopens | Example Daily");
  }

  @Test
  void aLinkWithoutAStatedLanguageReadsItFromItsTitle() {
    List<TopicSighting> links =
        TopicAnswers.links(
            rows(
                link("https://news.example/a", "봄 소풍 안내", null, 3, 3),
                link("https://news.example/b", "やまびこの時刻表", "", 3, 3),
                link("https://news.example/c", "Ferry timetable", null, 3, 3),
                link("https://news.example/d", "山彦通信", "ja", 3, 3)),
            "a.example");

    assertThat(links).extracting(TopicSighting::language).containsExactly("ko", "ja", null, "ja");
  }

  @Test
  void thisServersOwnTagsAndLinksCountTheirAccounts() {
    List<TopicSighting> tags =
        TopicAnswers.ownTags(
            List.of(new NoteTopicReader.Tag("소풍", 4), new NoteTopicReader.Tag(" ", 9)), "kurl.me");
    List<TopicSighting> links =
        TopicAnswers.ownLinks(
            List.of(
                new NoteTopicReader.Link("http://news.example/plain", "Plain", null, 9),
                new NoteTopicReader.Link(
                    "https://news.example/a", "봄 소풍 안내", "https://img.example/a.png", 3),
                new NoteTopicReader.Link("https://news.example/b", null, null, 9)),
            "kurl.me");

    assertThat(tags).hasSize(1);
    assertThat(tags.getFirst().host()).isEqualTo("kurl.me");
    assertThat(tags.getFirst().people()).isEqualTo(4);
    assertThat(tags.getFirst().language()).isEqualTo("ko");
    assertThat(links).hasSize(1);
    assertThat(links.getFirst().place()).isEqualTo(1);
    assertThat(links.getFirst().people()).isEqualTo(3);
    assertThat(links.getFirst().language()).isEqualTo("ko");
    assertThat(links.getFirst().article().source()).isNull();
    assertThat(links.getFirst().article().image()).isEqualTo("https://img.example/a.png");
  }
}
