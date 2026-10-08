package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.RemoteNotes;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RemoteNoteParserTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PUBLIC = "https://www.w3.org/ns/activitystreams#Public";
  private final RemoteNoteParser parser =
      new RemoteNoteParser(
          new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")));

  private static JsonNode json(String raw) {
    return JSON.readTree(raw);
  }

  private String body(String html) {
    return parser.body(json("{\"content\":" + JSON.writeValueAsString(html) + "}"));
  }

  @Test
  void htmlBecomesPlainTextWithParagraphsAndLineBreaks() {
    assertThat(body("<p>first<br>line</p><p>second &amp; last</p>"))
        .isEqualTo("first\nline\n\nsecond & last");
    assertThat(body("plain words")).isEqualTo("plain words");
    assertThat(body("<p>a</p><blockquote><p>quoted</p></blockquote><p>b</p>"))
        .isEqualTo("a\n\nquoted\n\nb");
    assertThat(parser.body(json("{\"contentMap\":{\"ja\":\"<p>こんにちは</p>\"}}"))).isEqualTo("こんにちは");
    assertThat(parser.body(json("{}"))).isEmpty();
  }

  @Test
  void mentionsElsewhereKeepTheirServerAndMembersStayShort() {
    assertThat(
            body(
                "<p><span class=\"h-card\"><a href=\"https://other.example/@bob\" class=\"u-url"
                    + " mention\">@<span>bob</span></a></span> and <span class=\"h-card\"><a"
                    + " href=\"https://blog.kurl.me/@yuki\" class=\"u-url mention\">@<span>yuki</span></a></span></p>"))
        .isEqualTo("@bob@other.example and @yuki");
    assertThat(body("<a href=\"https://x.example/@c\" class=\"mention\">@c@x.example</a>"))
        .isEqualTo("@c@x.example");
  }

  @Test
  void linksBecomeTheirAddressAndHashtagsTheirName() {
    assertThat(
            body(
                "<a href=\"https://example.com/a/very/long/path\" rel=\"nofollow\"><span"
                    + " class=\"invisible\">https://</span><span class=\"ellipsis\">example.com/a/very/lo</span><span"
                    + " class=\"invisible\">ng/path</span></a>"))
        .isEqualTo("https://example.com/a/very/long/path");
    assertThat(
            body(
                "<a href=\"https://social.example/tags/cats\" class=\"mention hashtag\">#<span>cats</span></a>"))
        .isEqualTo("#cats");
    assertThat(body("<a href=\"https://example.com/post\">read this</a>"))
        .isEqualTo("read this (https://example.com/post)");
    assertThat(body("<a>bare</a>")).isEqualTo("bare");
  }

  @Test
  void aVeryLongNoteIsCutWithAnEllipsis() {
    String cut = body("<p>" + "가".repeat(6000) + "</p>");
    assertThat(cut.codePointCount(0, cut.length())).isEqualTo(RemoteNoteParser.MAX_BODY);
    assertThat(cut).endsWith("…");
  }

  @Test
  void addressingDecidesVisibilityAsOnMastodon() {
    assertThat(RemoteNoteParser.visibility(Set.of(PUBLIC), Set.of())).isEqualTo("public");
    assertThat(RemoteNoteParser.visibility(Set.of("as:Public"), Set.of())).isEqualTo("public");
    assertThat(
            RemoteNoteParser.visibility(
                Set.of("https://m.example/users/a/followers"), Set.of(PUBLIC)))
        .isEqualTo("unlisted");
    assertThat(RemoteNoteParser.visibility(Set.of("https://m.example/users/a/followers"), Set.of()))
        .isEqualTo("private");
    assertThat(RemoteNoteParser.visibility(Set.of("https://kurl.me/ap/actors/pid"), Set.of()))
        .isEqualTo("direct");
  }

  @Test
  void aNoteCarriesItsAddressedMembersImagesAndWarning() {
    JsonNode object =
        json(
            """
            {"id":"https://m.example/s/1","type":"Note","url":["https://m.example/@a/1"],
             "contentMap":{"pt-BR":"<p>olá</p>"},
             "summary":"<p>spoilers</p>","sensitive":true,"published":"not a date",
             "inReplyTo":"https://kurl.me/ap/notes/7",
             "to":["https://kurl.me/ap/actors/pid"],"cc":["https://m.example/users/a/followers"],
             "tag":[{"type":"Mention","href":"https://kurl.me/ap/actors/other"},
                    {"type":"Hashtag","href":"https://m.example/tags/x"}],
             "attachment":[
               {"type":"Document","mediaType":"image/png","url":"https://files.m.example/1.png","name":"a cat","width":1200,"height":900},
               {"type":"Document","mediaType":"video/mp4","url":"https://files.m.example/2.mp4","width":"720","height":1280.5},
               {"type":"Image","url":{"href":"https://files.m.example/3.jpg"}},
               {"type":"Document","mediaType":"image/png","url":"http://insecure.example/4.png"},
               {"type":"Audio","url":"https://files.m.example/5.mp3"},
               {"type":"Document","mediaType":"application/pdf","url":"https://files.m.example/6.pdf"}]}
            """);
    RemoteNoteParser.Parsed parsed = parser.parse(object, json("{}"));

    assertThat(parsed.uri()).isEqualTo("https://m.example/s/1");
    assertThat(parsed.url()).isEqualTo("https://m.example/@a/1");
    assertThat(parsed.contentWarning()).isEqualTo("spoilers");
    assertThat(parsed.sensitive()).isTrue();
    assertThat(parsed.publishedAt()).isNull();
    assertThat(parsed.visibility()).isEqualTo("private");
    assertThat(parsed.inReplyToLocalId()).contains(7L);
    assertThat(parsed.addressedPublicIds()).containsExactly("pid", "other");
    assertThat(parsed.language()).isEqualTo("pt");
    assertThat(
            parser.parse(json("{\"id\":\"x\",\"content\":\"<p>hi</p>\"}"), json("{}")).language())
        .isNull();
    assertThat(parsed.media())
        .containsExactly(
            new RemoteNotes.Media("https://files.m.example/1.png", "a cat", "image/png", 1200, 900),
            new RemoteNotes.Media("https://files.m.example/2.mp4", null, "video/mp4"),
            new RemoteNotes.Media("https://files.m.example/3.jpg", null, "image/jpeg"),
            new RemoteNotes.Media("https://files.m.example/5.mp3", null, "audio/mpeg"));
    assertThat(parser.addressesUs(object, json("{}"))).isTrue();
    assertThat(
            parser.addressesUs(
                json("{\"tag\":[{\"type\":\"Mention\",\"href\":\"https://kurl.me/ap/actors/x\"}]}"),
                json("{}")))
        .isTrue();
    assertThat(parser.addressesUs(json("{\"cc\":\"" + PUBLIC + "\"}"), json("{}"))).isFalse();
  }

  @Test
  void anEditTimeIsReadFromUpdated() {
    assertThat(RemoteNoteParser.updated(json("{\"updated\":\"2026-10-07T02:00:00Z\"}")))
        .hasToString("2026-10-07T02:00:00Z");
    assertThat(RemoteNoteParser.updated(json("{}"))).isNull();
  }
}
