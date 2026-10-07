package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.Image;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Quote;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoteDocumentsTest {

  private static final Instant CREATED = Instant.parse("2026-10-06T01:02:03.456Z");
  private final NoteDocuments documents =
      new NoteDocuments(
          new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")));

  private static NoteSnapshot note(String body, Quote quote, List<Image> images, Long reply) {
    return new NoteSnapshot(42L, 7L, "yuki", body, CREATED, null, reply, quote, images);
  }

  @Test
  void userTextIsEscapedParagraphedAndOnlyUrlsBecomeLinks() {
    String html =
        documents.content(
            note(
                "<b>hi</b> & see https://example.com/a?b=1&c=2.\nnext line\n\nsecond <script>",
                null,
                List.of(),
                null));

    assertThat(html)
        .isEqualTo(
            "<p>&lt;b&gt;hi&lt;/b&gt; &amp; see <a href=\"https://example.com/a?b=1&amp;c=2\""
                + " rel=\"nofollow noopener noreferrer\" target=\"_blank\">"
                + "https://example.com/a?b=1&amp;c=2</a>.<br>next line</p>"
                + "<p>second &lt;script&gt;</p>");
  }

  @Test
  void unlistedAndFollowersOnlyNotesAreAddressedAsMastodonDoes() {
    NoteSnapshot unlisted =
        new NoteSnapshot(
            42L,
            7L,
            "yuki",
            "quiet",
            CREATED,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            false,
            NoteSnapshotReader.Visibility.UNLISTED);
    NoteSnapshot followersOnly =
        new NoteSnapshot(
            42L,
            7L,
            "yuki",
            "close",
            CREATED,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            false,
            NoteSnapshotReader.Visibility.PRIVATE);

    Map<String, Object> quiet = documents.create(unlisted, "pid");
    assertThat(quiet.get("to")).isEqualTo(List.of("https://kurl.me/ap/actors/pid/followers"));
    assertThat(quiet.get("cc")).isEqualTo(List.of("https://www.w3.org/ns/activitystreams#Public"));
    Map<String, Object> close = documents.note(followersOnly, "pid");
    assertThat(close.get("to")).isEqualTo(List.of("https://kurl.me/ap/actors/pid/followers"));
    assertThat(close.get("cc")).isEqualTo(List.of());
  }

  @Test
  void aNoteInAKnownLanguageCarriesItAsTheContentMapKey() {
    NoteSnapshot korean =
        new NoteSnapshot(
            42L,
            7L,
            "yuki",
            "안녕",
            CREATED,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            false,
            NoteSnapshotReader.Visibility.PUBLIC,
            null,
            "ko");

    Map<String, Object> note = documents.note(korean, "pid");

    assertThat(note.get("contentMap")).isEqualTo(Map.of("ko", note.get("content")));
    assertThat(documents.note(note("plain", null, List.of(), null), "pid"))
        .doesNotContainKey("contentMap");
  }

  @Test
  void aContentWarningTravelsAsTheSummaryAndMarksTheNoteSensitive() {
    NoteSnapshot warned =
        new NoteSnapshot(
            42L, 7L, "yuki", "spoilers", CREATED, null, null, null, List.of(), null, "결말 포함", true);

    Map<String, Object> note = documents.note(warned, "pid");

    assertThat(note.get("summary")).isEqualTo("결말 포함");
    assertThat(note.get("sensitive")).isEqualTo(true);
    assertThat(documents.note(note("plain", null, List.of(), null), "pid"))
        .containsEntry("summary", null)
        .containsEntry("sensitive", false);
  }

  @Test
  @SuppressWarnings("unchecked")
  void hashtagsLinkToTheTagPageAndTravelAsHashtagObjects() {
    Map<String, Object> object =
        documents.note(
            note(
                "#스프링 it's https://example.com/a#frag and a#b #Spring #123 &#tag",
                null,
                List.of(),
                null),
            "pid");

    assertThat((String) object.get("content"))
        .isEqualTo(
            "<p><a href=\"https://blog.kurl.me/tags/%EC%8A%A4%ED%94%84%EB%A7%81?view=notes\""
                + " class=\"mention hashtag\" rel=\"tag\">#<span>스프링</span></a> it&#39;s"
                + " <a href=\"https://example.com/a#frag\" rel=\"nofollow noopener noreferrer\""
                + " target=\"_blank\">https://example.com/a#frag</a> and a#b"
                + " <a href=\"https://blog.kurl.me/tags/Spring?view=notes\" class=\"mention hashtag\""
                + " rel=\"tag\">#<span>Spring</span></a> #123 &amp;"
                + "<a href=\"https://blog.kurl.me/tags/tag?view=notes\" class=\"mention hashtag\""
                + " rel=\"tag\">#<span>tag</span></a></p>");
    assertThat((List<Map<String, Object>>) object.get("tag"))
        .containsExactly(
            Map.of(
                "type", "Hashtag",
                "href", "https://blog.kurl.me/tags/%EC%8A%A4%ED%94%84%EB%A7%81?view=notes",
                "name", "#스프링"),
            Map.of(
                "type", "Hashtag",
                "href", "https://blog.kurl.me/tags/Spring?view=notes",
                "name", "#Spring"),
            Map.of(
                "type", "Hashtag",
                "href", "https://blog.kurl.me/tags/tag?view=notes",
                "name", "#tag"));
  }

  @Test
  void aQuotedBlogPostIsLinkedAndAnImageOnlyNoteHasNoTextParagraph() {
    String html =
        documents.content(note("", new Quote("Essay <1>", "my-essay", "yuki"), List.of(), null));

    assertThat(html)
        .isEqualTo("<p><a href=\"https://blog.kurl.me/@yuki/my-essay\">Essay &lt;1&gt;</a></p>");
  }

  @Test
  void aQuotedNoteCarriesTheQuoteFieldsAndAnReFallbackForServersWithoutThem() {
    NoteSnapshot quoting =
        new NoteSnapshot(
            42L,
            7L,
            "yuki",
            "agreed",
            CREATED,
            null,
            null,
            null,
            List.of(),
            new QuotedNote(9L, "mio"),
            null,
            false);

    Map<String, Object> note = documents.note(quoting, "pid");

    assertThat(note.get("quoteUrl")).isEqualTo("https://kurl.me/ap/notes/9");
    assertThat(note.get("quoteUri")).isEqualTo("https://kurl.me/ap/notes/9");
    assertThat(note.get("_misskey_quote")).isEqualTo("https://kurl.me/ap/notes/9");
    assertThat(note.get("content"))
        .isEqualTo(
            "<p>agreed</p><p class=\"quote-inline\">RE: <a href=\"https://blog.kurl.me/@mio/notes/9\">"
                + "https://blog.kurl.me/@mio/notes/9</a></p>");
    assertThat(documents.note(note("x", null, List.of(), null), "pid"))
        .doesNotContainKeys("quoteUrl", "quoteUri", "_misskey_quote");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aRepostIsAnAnnounceOfTheNoteAndUndoingItEmbedsThatAnnounce() {
    Map<String, Object> announce = documents.announce(900L, 42L, "reposter", "author");

    assertThat(announce.get("id")).isEqualTo("https://kurl.me/ap/reposts/900");
    assertThat(announce.get("type")).isEqualTo("Announce");
    assertThat(announce.get("actor")).isEqualTo("https://kurl.me/ap/actors/reposter");
    assertThat(announce.get("object")).isEqualTo("https://kurl.me/ap/notes/42");
    assertThat(announce.get("to"))
        .isEqualTo(List.of("https://www.w3.org/ns/activitystreams#Public"));
    assertThat(announce.get("cc"))
        .isEqualTo(
            List.of(
                "https://kurl.me/ap/actors/reposter/followers",
                "https://kurl.me/ap/actors/author"));

    Map<String, Object> undo = documents.undoAnnounce(900L, 42L, "reposter");
    assertThat(undo.get("id")).isEqualTo("https://kurl.me/ap/reposts/900#undo");
    assertThat(undo.get("type")).isEqualTo("Undo");
    assertThat(undo.get("actor")).isEqualTo("https://kurl.me/ap/actors/reposter");
    Map<String, Object> undone = (Map<String, Object>) undo.get("object");
    assertThat(undone)
        .containsEntry("id", "https://kurl.me/ap/reposts/900")
        .containsEntry("type", "Announce")
        .containsEntry("actor", "https://kurl.me/ap/actors/reposter")
        .containsEntry("object", "https://kurl.me/ap/notes/42");
  }

  @Test
  @SuppressWarnings("unchecked")
  void createCarriesTheNoteAddressedToThePublicAndFollowers() {
    Map<String, Object> create =
        documents.create(
            note("hello", null, List.of(new Image("https://cdn/a.png", "image/png", "cat")), 41L),
            "pid");

    assertThat(create.get("id")).isEqualTo("https://kurl.me/ap/notes/42/activity");
    assertThat(create.get("type")).isEqualTo("Create");
    assertThat(create.get("actor")).isEqualTo("https://kurl.me/ap/actors/pid");
    assertThat(create.get("to")).isEqualTo(List.of("https://www.w3.org/ns/activitystreams#Public"));
    assertThat(create.get("cc")).isEqualTo(List.of("https://kurl.me/ap/actors/pid/followers"));
    Map<String, Object> note = (Map<String, Object>) create.get("object");
    assertThat(note.get("id")).isEqualTo("https://kurl.me/ap/notes/42");
    assertThat(note.get("attributedTo")).isEqualTo("https://kurl.me/ap/actors/pid");
    assertThat(note.get("published")).isEqualTo("2026-10-06T01:02:03Z");
    assertThat(note).doesNotContainKey("updated");
    assertThat(note.get("url")).isEqualTo("https://blog.kurl.me/@yuki/notes/42");
    assertThat(note.get("inReplyTo")).isEqualTo("https://kurl.me/ap/notes/41");
    assertThat((List<Map<String, Object>>) note.get("attachment"))
        .containsExactly(
            Map.of(
                "type", "Document",
                "mediaType", "image/png",
                "url", "https://cdn/a.png",
                "name", "cat"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void eachEditIsANewUpdateAndDeleteSendsATombstone() {
    NoteSnapshot edited =
        new NoteSnapshot(
            42L, 7L, "yuki", "x", CREATED, Instant.ofEpochMilli(1_000), null, null, List.of());

    Map<String, Object> update = documents.update(edited, "pid");
    assertThat(update.get("id")).isEqualTo("https://kurl.me/ap/notes/42#updates/1000");
    assertThat(((Map<String, Object>) update.get("object")).get("updated"))
        .isEqualTo("1970-01-01T00:00:01Z");
    assertThat(((Map<String, Object>) update.get("object")).get("inReplyTo")).isNull();
    assertThat(documents.update(note("x", null, List.of(), null), "pid").get("id"))
        .isEqualTo("https://kurl.me/ap/notes/42#updates/0");

    Map<String, Object> delete = documents.delete(42L, "pid");
    assertThat(delete.get("id")).isEqualTo("https://kurl.me/ap/notes/42#delete");
    assertThat(delete.get("object"))
        .isEqualTo(Map.of("id", "https://kurl.me/ap/notes/42", "type", "Tombstone"));
  }

  private static NoteSnapshot polled(boolean multiple, boolean closed) {
    return new NoteSnapshot(
        42L,
        7L,
        "yuki",
        "어디서 볼까?",
        CREATED,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        false,
        NoteSnapshotReader.Visibility.PUBLIC,
        new NoteSnapshotReader.Poll(
            List.of(
                new NoteSnapshotReader.PollOption("강남", 3),
                new NoteSnapshotReader.PollOption("홍대", 1)),
            Instant.parse("2026-10-08T01:02:03.999Z"),
            multiple,
            4,
            closed));
  }

  @Test
  void aPollIsAQuestionWhoseOptionsCountTheirVotesAsReplies() {
    Map<String, Object> single = documents.note(polled(false, false), "abc");

    assertThat(single.get("type")).isEqualTo("Question");
    assertThat(single.get("oneOf"))
        .isEqualTo(
            List.of(
                Map.of(
                    "type",
                    "Note",
                    "name",
                    "강남",
                    "replies",
                    Map.of("type", "Collection", "totalItems", 3L)),
                Map.of(
                    "type",
                    "Note",
                    "name",
                    "홍대",
                    "replies",
                    Map.of("type", "Collection", "totalItems", 1L))));
    assertThat(single).doesNotContainKey("anyOf").doesNotContainKey("closed");
    assertThat(single.get("endTime")).isEqualTo("2026-10-08T01:02:03Z");
    assertThat(single.get("votersCount")).isEqualTo(4L);

    Map<String, Object> multiple = documents.note(polled(true, true), "abc");
    assertThat(multiple).containsKey("anyOf").doesNotContainKey("oneOf");
    assertThat(multiple.get("closed")).isEqualTo("2026-10-08T01:02:03Z");
    assertThat(documents.note(note("plain", null, List.of(), null), "abc").get("type"))
        .isEqualTo("Note");
  }

  @Test
  void anEndedPollGoesOutAsAnUpdateOfItsOwn() {
    Map<String, Object> update = documents.pollEnded(polled(false, true), "abc");

    assertThat(update.get("id")).isEqualTo("https://kurl.me/ap/notes/42#updates/poll-ended");
    assertThat(update.get("type")).isEqualTo("Update");
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) update.get("object");
    assertThat(object.get("closed")).isEqualTo("2026-10-08T01:02:03Z");
  }
}
