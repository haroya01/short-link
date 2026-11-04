package com.example.short_link.note.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.note.application.write.NoteLinkPreviewRefill;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NoteLinkPreviewRefillHttpQueryContractTest extends OperationalHttpJourneySupport {

  private static final String SHARED = "https://youtu.be/dQw4w9WgXcQ?si=abc";
  private static final String OLDER = "https://older.example/post";

  @Autowired private NoteLinkPreviewRefill refill;

  @Test
  void aReleaseRefillsMissingCardsNewestFirstOnceAndReopensGivenUpShortLinks() throws Exception {
    Actor author = actor("refill", false);
    long older = post("note-refill-post-link", author, "예전에 남긴 " + OLDER);
    when(externalMetadata.fetch("https://carded.example/"))
        .thenReturn(new OgMetadata("Carded", null, null));
    long carded = post("note-refill-post-carded", author, "카드가 있는 https://carded.example/");
    long plain = post("note-refill-post-plain", author, "링크가 없는 글");
    long polled = post("note-refill-post-link", author, "투표 https://poll.example/");
    long quoting = post("note-refill-post-link", author, "인용 https://quote.example/");
    long pictured = post("note-refill-post-link", author, "사진 https://media.example/");
    long shared = post("note-refill-post-link", author, "이거 봐 " + SHARED + ".");
    jdbc.update("UPDATE note SET poll_options = '[\"a\",\"b\"]' WHERE id = ?", polled);
    jdbc.update("UPDATE note SET quoted_note_id = ? WHERE id = ?", carded, quoting);
    jdbc.update(
        "INSERT INTO note_media (note_id, position, storage_key, url, content_type, created_at)"
            + " VALUES (?, 0, 'notes/refill.png', 'https://cdn.example/refill.png', 'image/png',"
            + " NOW(6))",
        pictured);
    long gaveUp = shorten("note-refill-shorten-first", author, "https://gave-up.example/");
    long retrying = shorten("note-refill-shorten", author, "https://retrying.example/");
    jdbc.update(
        "UPDATE link SET og_fetch_status = 'ERROR', og_fetch_attempts = 3 WHERE id = ?", gaveUp);
    jdbc.update(
        "UPDATE link_og_metadata SET og_fetch_status = 'ERROR', og_fetch_attempts = 3"
            + " WHERE link_id = ?",
        gaveUp);
    assertThat(cardTitle(shared)).isNull();
    assertThat(cardTitle(carded)).isEqualTo("Carded");
    awaitAsyncWork();
    clearInvocations(externalMetadata);

    deliverBackgroundWork("note-refill-loads", refill::step);
    deliverBackgroundWork("note-refill-starts", refill::step);
    assertThat(ogFetch(gaveUp, "link")).isEqualTo("RETRYABLE/0");
    assertThat(ogFetch(gaveUp, "link_og_metadata")).isEqualTo("RETRYABLE/0");
    assertThat(ogFetch(retrying, "link")).isEqualTo("RETRYABLE/1");
    assertThat(ogFetch(retrying, "link_og_metadata")).isEqualTo("RETRYABLE/1");

    when(externalMetadata.fetch(SHARED))
        .thenReturn(
            new OgMetadata(
                "Never Gonna Give You Up", "Rick Astley", "https://i.ytimg.com/vi/x/max.jpg"));
    deliverBackgroundWork("note-refill-newest-gets-its-card", refill::step);
    assertThat(cardTitle(shared)).isEqualTo("Never Gonna Give You Up");
    deliverBackgroundWork("note-refill-waits-its-pace", refill::step);
    assertThat(refillRow()).containsEntry("cursor_id", shared);

    Thread.sleep(1_100);
    deliverBackgroundWork("note-refill-older-stays-bare", refill::step);
    assertThat(cardTitle(older)).isNull();
    Thread.sleep(1_100);
    deliverBackgroundWork("note-refill-finishes", refill::step);
    deliverBackgroundWork("note-refill-finished-stays-closed", refill::step);
    deliverBackgroundWork("note-refill-idle", refill::step);

    verify(externalMetadata).fetch(SHARED);
    verify(externalMetadata).fetch(OLDER);
    verifyNoMoreInteractions(externalMetadata);
    assertThat(cardTitle(carded)).isEqualTo("Carded");
    for (long untouched : new long[] {plain, polled, quoting, pictured}) {
      assertThat(count("note_link_preview", "note_id = ?", untouched)).isZero();
    }
    assertThat(refillRow())
        .containsEntry("targets", 2)
        .containsEntry("links_reopened", 1)
        .containsEntry("filled", 1)
        .containsEntry("missed", 1)
        .containsEntry("cursor_id", older)
        .containsEntry("open", 0L);
  }

  private long post(String id, Actor author, String body) throws Exception {
    return step(id, "POST", "/api/v1/notes", author, Map.of("body", body), 201).path("id").asLong();
  }

  private long shorten(String id, Actor author, String url) throws Exception {
    String code =
        step(id, "POST", "/api/v1/links", author, Map.of("url", url), 201)
            .path("shortCode")
            .asText();
    return jdbc.queryForObject("SELECT id FROM link WHERE short_code = ?", Long.class, code);
  }

  private String cardTitle(long noteId) {
    return jdbc
        .queryForList("SELECT title FROM note_link_preview WHERE note_id = ?", String.class, noteId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private String ogFetch(long linkId, String table) {
    String key = table.equals("link") ? "id" : "link_id";
    return jdbc.queryForObject(
        "SELECT CONCAT(og_fetch_status, '/', og_fetch_attempts) FROM "
            + table
            + " WHERE "
            + key
            + " = ?",
        String.class,
        linkId);
  }

  private Map<String, Object> refillRow() {
    return jdbc.queryForMap(
        "SELECT targets, links_reopened, filled, missed, cursor_id, finished_at IS NULL AS open"
            + " FROM note_link_preview_refill WHERE name = 'cross-host-redirects'");
  }
}
