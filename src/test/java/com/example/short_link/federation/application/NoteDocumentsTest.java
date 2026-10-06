package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;

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
            new QuotedNote(9L, "mio"));

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
}
