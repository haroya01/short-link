package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class PostNoteQuoteHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Value("${short-link.blog-base-url}")
  private String blogBaseUrl;

  private final List<Long> authors = new ArrayList<>();

  @AfterEach
  void removeWhatThisJourneyWrote() {
    for (Long author : authors) {
      for (String table : List.of("post_block", "post_revision", "post_search_text")) {
        jdbc.update(
            "DELETE t FROM " + table + " t JOIN posts p ON p.id = t.post_id WHERE p.user_id = ?",
            author);
      }
      jdbc.update("DELETE FROM posts WHERE user_id = ?", author);
      jdbc.update("DELETE FROM note WHERE user_id = ?", author);
    }
  }

  @Test
  void aPostCarryingANoteAsACardIsListedUnderItAndCountsAsAQuoteUntilTheCardGoes()
      throws Exception {
    Actor writer = actor("quoted-writer", false);
    Actor blogger = actor("quoting-blogger", false);
    authors.add(writer.id());
    authors.add(blogger.id());
    long first = note(writer, "블로그 글에 실릴 노트");
    long second = note(writer, "나중에 대신 실릴 노트");
    long postId =
        draft(
            blogger,
            List.of(
                Map.of("type", "PARAGRAPH", "content", "이 노트를 보세요"),
                Map.of("type", "EMBED", "content", noteUrl(writer, first))));

    step(
        "post-publish-quoting-note",
        "POST",
        "/api/v1/posts/" + postId + "/publish",
        blogger,
        null,
        200);
    assertThat(quotedNotes(postId)).containsExactly(first);

    var quoting =
        step(
            "note-quoting-posts",
            "GET",
            "/api/v1/public/notes/" + first + "/posts",
            null,
            null,
            200);
    assertThat(quoting.path("items")).hasSize(1);
    assertThat(quoting.path("items").get(0).path("id").asLong()).isEqualTo(postId);
    assertThat(quoting.path("items").get(0).path("author").path("username").asText())
        .isEqualTo(blogger.username());
    assertThat(quoting.path("hasNext").asBoolean()).isFalse();

    var thread =
        step("note-thread-quoted-in-post", "GET", "/api/v1/public/notes/" + first, null, null, 200);
    assertThat(thread.path("note").path("quoteCount").asLong()).isEqualTo(1);

    step(
        "post-blocks-replace-published-quote",
        "PUT",
        "/api/v1/posts/" + postId + "/blocks",
        blogger,
        Map.of("blocks", List.of(Map.of("type", "EMBED", "content", noteUrl(writer, second)))),
        200);
    assertThat(quotedNotes(postId)).containsExactly(second);

    var after =
        step(
            "note-quoting-posts-after-edit",
            "GET",
            "/api/v1/public/notes/" + first + "/posts",
            null,
            null,
            200);
    assertThat(after.path("items")).isEmpty();
  }

  @Test
  void aDraftsCardsStayUnlistedAndItsAutosavesWriteNothingForThem() throws Exception {
    Actor writer = actor("draft-writer", false);
    Actor blogger = actor("draft-blogger", false);
    authors.add(writer.id());
    authors.add(blogger.id());
    long noteId = note(writer, "초안에만 실린 노트");
    long postId = draft(blogger, List.of());

    step(
        "post-blocks-replace-draft-quote",
        "PUT",
        "/api/v1/posts/" + postId + "/blocks",
        blogger,
        Map.of("blocks", List.of(Map.of("type", "EMBED", "content", noteUrl(writer, noteId)))),
        200);

    assertThat(quotedNotes(postId)).isEmpty();
  }

  private String noteUrl(Actor author, long noteId) {
    return blogBaseUrl + "/@" + author.username() + "/notes/" + noteId;
  }

  private long note(Actor author, String body) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at) VALUES (?, ?, NOW(6))", author.id(), body);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.id());
  }

  private long draft(Actor author, List<Map<String, String>> blocks) {
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, status, created_at, updated_at)"
            + " VALUES (?, 'quoting', 'Quoting a note', 'DRAFT', NOW(6), NOW(6))",
        author.id());
    long postId =
        jdbc.queryForObject(
            "SELECT id FROM posts WHERE user_id = ? AND slug = 'quoting'", Long.class, author.id());
    for (int i = 0; i < blocks.size(); i++) {
      jdbc.update(
          "INSERT INTO post_block (post_id, block_type, content, block_order, created_at,"
              + " updated_at) VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
          postId,
          blocks.get(i).get("type"),
          blocks.get(i).get("content"),
          i);
    }
    return postId;
  }

  private List<Long> quotedNotes(long postId) {
    return jdbc.queryForList(
        "SELECT note_id FROM post_note_quote WHERE post_id = ? ORDER BY note_id",
        Long.class,
        postId);
  }
}
