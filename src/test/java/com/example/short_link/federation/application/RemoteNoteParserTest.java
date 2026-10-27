package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.common.note.RemoteNotes.Kind;
import com.example.short_link.common.note.RemoteNotes.Shape;
import com.example.short_link.testsupport.FederationFixtures;
import java.time.Instant;
import java.util.List;
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
    assertThat(RemoteNoteParser.visibility(Set.of(PUBLIC), Set.of()))
        .isEqualTo(NoteVisibility.PUBLIC);
    assertThat(RemoteNoteParser.visibility(Set.of("as:Public"), Set.of()))
        .isEqualTo(NoteVisibility.PUBLIC);
    assertThat(
            RemoteNoteParser.visibility(
                Set.of("https://m.example/users/a/followers"), Set.of(PUBLIC)))
        .isEqualTo(NoteVisibility.UNLISTED);
    assertThat(RemoteNoteParser.visibility(Set.of("https://m.example/users/a/followers"), Set.of()))
        .isEqualTo(NoteVisibility.PRIVATE);
    assertThat(RemoteNoteParser.visibility(Set.of("https://kurl.me/ap/actors/pid"), Set.of()))
        .isEqualTo(NoteVisibility.DIRECT);
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
    assertThat(parsed.visibility()).isEqualTo(NoteVisibility.PRIVATE);
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
  void aNoteAddressesUsExactlyWhenItNamesSomeoneHere() {
    String us = "https://kurl.me/ap/actors/pid";
    List<String[]> cases =
        List.of(
            new String[] {"{\"to\":[\"" + us + "\"]}", "{}"},
            new String[] {"{\"cc\":\"" + us + "\"}", "{}"},
            new String[] {"{}", "{\"to\":[\"" + us + "\"]}"},
            new String[] {"{\"to\":[\"" + PUBLIC + "\"]}", "{\"to\":[\"" + us + "\"]}"},
            new String[] {"{\"to\":[{\"id\":\"" + us + "\"}]}", "{}"},
            new String[] {"{\"tag\":[{\"type\":\"Mention\",\"href\":\"" + us + "\"}]}", "{}"},
            new String[] {"{\"tag\":[{\"type\":\"Hashtag\",\"href\":\"" + us + "\"}]}", "{}"},
            new String[] {"{\"tag\":{\"type\":\"Mention\",\"href\":\"" + us + "\"}}", "{}"},
            new String[] {
              "{\"to\":[\"https://kurl.me/ap/actors/pid/followers\"],"
                  + "\"tag\":[{\"type\":\"Mention\",\"href\":\"https://elsewhere.example/u/a\"}]}",
              "{}"
            },
            new String[] {"{\"cc\":[\"" + PUBLIC + "\"]}", "{\"cc\":[\"" + us + "\"]}"});
    for (String[] pair : cases) {
      JsonNode object = json(pair[0]);
      JsonNode activity = json(pair[1]);
      assertThat(parser.addressesUs(object, activity))
          .as("%s in %s", pair[0], pair[1])
          .isEqualTo(!parser.parse(object, activity).addressedPublicIds().isEmpty());
    }
    assertThat(cases.stream().filter(pair -> parser.addressesUs(json(pair[0]), json(pair[1]))))
        .hasSize(5);
  }

  @Test
  void theKindsKeptAreNotesQuestionsArticlesPagesAndVideos() {
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Note\"}"))).contains(Kind.NOTE);
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Question\"}"))).contains(Kind.QUESTION);
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Article\"}"))).contains(Kind.ARTICLE);
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Page\"}"))).contains(Kind.PAGE);
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Video\"}"))).contains(Kind.VIDEO);
    assertThat(RemoteNoteParser.kindOf(json("{\"type\":\"Event\"}"))).isEmpty();
    assertThat(RemoteNoteParser.kindOf(json("{}"))).isEmpty();
    assertThat(RemoteNoteParser.kindOf(json("\"https://m.example/s/1\""))).isEmpty();
    assertThat(RemoteNoteParser.kindOf(null)).isEmpty();
  }

  @Test
  void aMastodonQuestionIsANoteCarryingItsServersCounts() {
    RemoteNoteParser.Parsed parsed =
        parser.parse(FederationFixtures.json("mastodon-question.json"), json("{}"));

    assertThat(parsed.body()).isEqualTo("Which editor do you write in?");
    assertThat(parsed.url()).isEqualTo("https://mastodon.example/@alice/113300000000000001");
    assertThat(parsed.visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(parsed.shape())
        .isEqualTo(
            new Shape(
                Kind.QUESTION,
                null,
                null,
                new RemoteNotes.Poll(
                    List.of("Vim", "Emacs", "Something else"),
                    List.of(7L, 3L, 2L),
                    12,
                    false,
                    Instant.parse("2026-10-11T09:00:00Z"),
                    false)));
  }

  @Test
  void aMultipleChoicePollClosedEarlyEndsWhenItClosed() {
    RemoteNotes.Poll poll =
        RemoteNoteParser.poll(
            json(
                """
                {"type":"Question","endTime":"2026-10-11T09:00:00Z",
                 "closed":"2026-10-10T21:00:00Z",
                 "anyOf":[{"name":"<b>Beans</b>\\nand peas","replies":{"totalItems":4}},
                          {"name":"%s","replies":{"totalItems":-3}},
                          {"name":"Squash","replies":{"totalItems":"2"}}]}
                """
                    .formatted("x".repeat(60))));

    assertThat(poll.multiple()).isTrue();
    assertThat(poll.options()).containsExactly("Beans and peas", "x".repeat(49) + "…", "Squash");
    assertThat(poll.votes()).containsExactly(4L, 0L, 0L);
    assertThat(poll.voters()).isEqualTo(4);
    assertThat(poll.endsAt()).isEqualTo(Instant.parse("2026-10-10T21:00:00Z"));
    assertThat(poll.closed()).isTrue();

    RemoteNotes.Poll ended =
        RemoteNoteParser.poll(
            json(
                """
                {"closed":true,"votersCount":"many",
                 "oneOf":[{"name":"a"},{"name":"b","replies":{"totalItems":1}}]}
                """));
    assertThat(ended.closed()).isTrue();
    assertThat(ended.endsAt()).isNull();
    assertThat(ended.voters()).isZero();
    assertThat(
            RemoteNoteParser.poll(
                    json(
                        """
                        {"endTime":"2026-10-11T09:00:00Z","closed":"2026-10-12T09:00:00Z",
                         "oneOf":[{"name":"a"},{"name":"b"}]}
                        """))
                .endsAt())
        .isEqualTo(Instant.parse("2026-10-11T09:00:00Z"));
  }

  @Test
  void aPollThatDoesNotFitIsLeftOffItsNote() {
    assertThat(RemoteNoteParser.poll(json("{\"oneOf\":[{\"name\":\"only\"}]}"))).isNull();
    assertThat(
            RemoteNoteParser.poll(
                json(
                    "{\"anyOf\":[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"},"
                        + "{\"name\":\"d\"},{\"name\":\"e\"}]}")))
        .isNull();
    assertThat(RemoteNoteParser.poll(json("{\"oneOf\":[{\"name\":\"a\"},{\"name\":\" \"}]}")))
        .isNull();
    assertThat(RemoteNoteParser.poll(json("{\"oneOf\":{\"name\":\"a\"}}"))).isNull();
    assertThat(RemoteNoteParser.poll(json("{}"))).isNull();
    assertThat(
            parser.parse(json("{\"type\":\"Question\",\"content\":\"pick\"}"), json("{}")).shape())
        .isEqualTo(new Shape(Kind.QUESTION, null, null, null));
  }

  @Test
  void aWordPressArticleIsACardWithItsTitleSummaryPictureAndAddress() {
    RemoteNoteParser.Parsed parsed =
        parser.parse(FederationFixtures.json("wordpress-article.json"), json("{}"));

    assertThat(parsed.shape())
        .isEqualTo(new Shape(Kind.ARTICLE, "What the garden taught me this year", null, null));
    assertThat(parsed.body()).isEqualTo("A season of tomatoes, slugs & patience. […]");
    assertThat(parsed.contentWarning()).isNull();
    assertThat(parsed.url())
        .isEqualTo("https://blog.example/2026/10/01/what-the-garden-taught-me/");
    assertThat(parsed.visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(parsed.media())
        .containsExactly(
            new RemoteNotes.Media(
                "https://blog.example/wp-content/uploads/2026/10/tomatoes.jpg",
                "Tomatoes ripening on the vine",
                "image/jpeg"));
  }

  @Test
  void anArticleWithoutASummaryKeepsOnlyTheStartOfItsText() {
    RemoteNoteParser.Parsed parsed =
        parser.parse(
            json(
                "{\"id\":\"https://blog.example/?p=7\",\"type\":\"Article\",\"name\":\"Long\","
                    + "\"contentMap\":{\"en\":\"<p>"
                    + "가".repeat(700)
                    + "</p>\"},\"image\":\"https://blog.example/cover.jpg\","
                    + "\"attachment\":[{\"type\":\"Image\",\"url\":\"https://blog.example/2.jpg\"}]}"),
            json("{}"));

    assertThat(parsed.body().codePointCount(0, parsed.body().length()))
        .isEqualTo(RemoteNoteParser.MAX_EXCERPT);
    assertThat(parsed.body()).endsWith("…");
    assertThat(parsed.media())
        .containsExactly(
            new RemoteNotes.Media("https://blog.example/cover.jpg", null, "image/jpeg"));
    assertThat(
            parser
                .parse(
                    json(
                        "{\"id\":\"https://blog.example/?p=8\",\"type\":\"Article\","
                            + "\"image\":\"http://blog.example/plain.jpg\"}"),
                    json("{}"))
                .media())
        .isEmpty();
  }

  @Test
  void aLemmyPageNamesItsCommunityAndKeepsItsImage() {
    RemoteNoteParser.Parsed parsed =
        parser.parse(FederationFixtures.json("lemmy-page.json"), json("{}"));

    assertThat(parsed.shape())
        .isEqualTo(
            new Shape(
                Kind.PAGE, "Raised beds: worth the lumber?", "gardening@lemmy.example", null));
    assertThat(parsed.body())
        .isEqualTo("Thinking about building raised beds this winter. Anyone regret it?");
    assertThat(parsed.url()).isNull();
    assertThat(parsed.visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(parsed.publishedAt()).isEqualTo(Instant.parse("2026-10-05T03:00:00.123456Z"));
    assertThat(parsed.media())
        .containsExactly(
            new RemoteNotes.Media(
                "https://lemmy.example/pictrs/image/raised-beds.jpeg", null, "image/jpeg"));
  }

  @Test
  void aCommunityIsFoundInToWithoutAudienceAndOnlyWhenItLooksLikeOne() {
    assertThat(
            community(
                "{\"type\":\"Page\",\"to\":[\""
                    + PUBLIC
                    + "\","
                    + "\"https://lemmy.example/c/gardening/\"]}"))
        .isEqualTo("gardening@lemmy.example");
    assertThat(community("{\"type\":\"Page\",\"to\":[\"https://lemmy.example/u/river\"]}"))
        .isNull();
    assertThat(community("{\"type\":\"Note\",\"to\":[\"https://lemmy.example/c/gardening\"]}"))
        .isNull();
    assertThat(
            community(
                "{\"type\":\"Note\",\"audience\":{\"id\":\"https://mbin.example/m/tech\","
                    + "\"type\":\"Group\"}}"))
        .isEqualTo("tech@mbin.example");
    assertThat(community("{\"type\":\"Page\",\"audience\":\"https://lemmy.example/c/a%20b\"}"))
        .isNull();
    assertThat(community("{\"type\":\"Page\",\"audience\":\"not a uri\"}")).isNull();
    assertThat(community("{\"type\":\"Page\",\"to\":[\"https://lemmy.example/c/bad name\"]}"))
        .isNull();
  }

  private String community(String object) {
    return parser.parse(json(object), json("{}")).shape().community();
  }

  @Test
  void aPeerTubeVideoKeepsItsTitleWidestThumbnailAndWatchPageButNoVideoFile() {
    RemoteNoteParser.Parsed parsed =
        parser.parse(FederationFixtures.json("peertube-video.json"), json("{}"));

    assertThat(parsed.shape())
        .isEqualTo(new Shape(Kind.VIDEO, "Building a raised bed in ten minutes", null, null));
    assertThat(parsed.url()).isEqualTo("https://peertube.example/w/kVBbnS8f4E5yDLs8JhzHmb");
    assertThat(parsed.body()).isEqualTo("Step by step, with **cheap** lumber and one saw.");
    assertThat(parsed.visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(parsed.media())
        .containsExactly(
            new RemoteNotes.Media(
                "https://peertube.example/lazy-static/previews/9c9de5e8.jpg",
                null,
                "image/jpeg",
                850,
                480));
    assertThat(
            parser
                .parse(
                    json(
                        "{\"id\":\"https://pt.example/v/1\",\"type\":\"Video\","
                            + "\"icon\":[{\"type\":\"Image\",\"url\":\"https://pt.example/a.jpg\"},"
                            + "{\"type\":\"Image\",\"url\":\"https://pt.example/b.jpg\",\"width\":100}],"
                            + "\"attachment\":[{\"type\":\"Image\",\"url\":\"https://pt.example/c.jpg\"}]}"),
                    json("{}"))
                .media())
        .extracting(RemoteNotes.Media::url)
        .containsExactly("https://pt.example/b.jpg");
    assertThat(
            parser
                .parse(
                    json(
                        "{\"id\":\"https://pt.example/v/2\",\"type\":\"Video\","
                            + "\"image\":{\"type\":\"Image\",\"url\":\"https://pt.example/d.jpg\"},"
                            + "\"attachment\":[{\"type\":\"Image\",\"url\":\"https://pt.example/e.jpg\"}]}"),
                    json("{}"))
                .media())
        .extracting(RemoteNotes.Media::url)
        .containsExactly("https://pt.example/d.jpg");
  }

  @Test
  void anAddressElsewhereOrOffTheWebIsDropped() {
    assertThat(url("{\"id\":\"https://m.example/s/1\",\"url\":\"https://phish.example/s/1\"}"))
        .isNull();
    assertThat(url("{\"id\":\"https://m.example/s/1\",\"url\":\"javascript:alert(1)\"}")).isNull();
    assertThat(url("{\"url\":\"https://m.example/s/1\"}")).isNull();
    assertThat(url("{\"id\":\"https://m.example/s/1\",\"url\":\"https://m.example/@a/1\"}"))
        .isEqualTo("https://m.example/@a/1");
    assertThat(
            url(
                "{\"id\":\"https://pt.example/v/1\",\"url\":[{\"mediaType\":\"video/mp4\","
                    + "\"href\":\"https://pt.example/1.mp4\"},{\"href\":\"https://pt.example/w/1\"}]}"))
        .isEqualTo("https://pt.example/1.mp4");
  }

  private String url(String object) {
    return parser.parse(json(object), json("{}")).url();
  }

  @Test
  void anEditTimeIsReadFromUpdated() {
    assertThat(RemoteNoteParser.updated(json("{\"updated\":\"2026-10-07T02:00:00Z\"}")))
        .hasToString("2026-10-07T02:00:00Z");
    assertThat(RemoteNoteParser.updated(json("{}"))).isNull();
  }
}
