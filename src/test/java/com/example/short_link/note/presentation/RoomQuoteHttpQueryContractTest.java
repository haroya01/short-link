package com.example.short_link.note.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class RoomQuoteHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Value("${short-link.blog-base-url}")
  private String blogBaseUrl;

  private final List<Long> members = new ArrayList<>();

  @AfterEach
  void removeWhatThisJourneyWrote() {
    for (Long member : members) {
      jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", member);
      for (String table : List.of("post_block", "post_revision", "post_search_text")) {
        jdbc.update(
            "DELETE t FROM " + table + " t JOIN posts p ON p.id = t.post_id WHERE p.user_id = ?",
            member);
      }
      jdbc.update("DELETE FROM posts WHERE user_id = ?", member);
      jdbc.update("DELETE FROM note WHERE user_id = ?", member);
    }
  }

  @Test
  void aRoomsNoteAndPostAreQuotedCountedAndListedInsideTheRoomAndNowhereElse() throws Exception {
    Actor owner = actor("quoted-owner", false);
    Actor friend = actor("quoting-friend", false);
    Actor outsider = actor("quoting-outsider", false);
    members.addAll(List.of(owner.id(), friend.id(), outsider.id()));
    long roomId = room(owner, friend);
    long roomNote = roomNote(owner, roomId);
    long roomPost = roomPost(owner, roomId, "PUBLISHED", List.of());

    var ofNote =
        step(
            "room-note-quote-room-note",
            "POST",
            "/api/v1/notes",
            friend,
            Map.of("body", "방 안에서 인용", "roomId", roomId, "quotedNoteId", roomNote),
            201);
    assertThat(ofNote.path("quotedNote").path("id").asLong()).isEqualTo(roomNote);
    var ofPost =
        step(
            "room-note-quote-room-post",
            "POST",
            "/api/v1/notes",
            friend,
            Map.of("body", "방 글 인용", "roomId", roomId, "quotedPostId", roomPost),
            201);
    assertThat(ofPost.path("quotedPost").path("audience").asString()).isEqualTo("ROOM");
    assertThat(ofPost.path("quotedPost").has("locked")).isFalse();
    var noteFromOutside =
        step(
            "room-note-quote-from-outside",
            "POST",
            "/api/v1/notes",
            friend,
            Map.of("body", "밖에서 인용", "quotedNoteId", roomNote),
            400);
    assertThat(noteFromOutside.path("code").asString()).isEqualTo("NOTE_NOT_SHAREABLE");
    var postFromOutside =
        step(
            "room-post-quote-from-outside",
            "POST",
            "/api/v1/notes",
            friend,
            Map.of("body", "밖에서 인용", "quotedPostId", roomPost),
            400);
    assertThat(postFromOutside.path("code").asString()).isEqualTo("NOTE_QUOTE_NOT_FOUND");

    long carrying =
        roomPost(
            friend,
            roomId,
            "DRAFT",
            List.of(blogBaseUrl + "/@" + owner.username() + "/notes/" + roomNote));
    step(
        "room-post-publish-quoting-room-note",
        "POST",
        "/api/v1/posts/" + carrying + "/publish",
        friend,
        null,
        200);
    assertThat(told(owner, "NOTE_QUOTE")).isOne();
    assertThat(told(owner, "POST_QUOTE")).isOne();
    assertThat(told(owner, "NOTE_EMBED")).isOne();

    var quoted =
        step(
            "room-note-thread-quoted",
            "GET",
            "/api/v1/public/notes/" + roomNote,
            friend,
            null,
            200);
    assertThat(quoted.path("note").path("quoteCount").asLong()).isEqualTo(2);
    var quoting =
        step(
            "room-note-thread-quoting-room-post",
            "GET",
            "/api/v1/public/notes/" + ofPost.path("id").asLong(),
            owner,
            null,
            200);
    assertThat(quoting.path("note").path("quotedPost").path("id").asLong()).isEqualTo(roomPost);

    var quotes =
        step(
            "room-note-quotes",
            "GET",
            "/api/v1/public/notes/" + roomNote + "/quotes",
            friend,
            null,
            200);
    assertThat(quotes.path("items")).hasSize(1);
    assertThat(quotes.path("items").get(0).path("id").asLong())
        .isEqualTo(ofNote.path("id").asLong());
    var quotesOutside =
        step(
            "room-note-quotes-outsider",
            "GET",
            "/api/v1/public/notes/" + roomNote + "/quotes",
            outsider,
            null,
            200);
    assertThat(quotesOutside.path("items")).isEmpty();

    var posts =
        step(
            "room-note-quoting-posts",
            "GET",
            "/api/v1/public/notes/" + roomNote + "/posts",
            owner,
            null,
            200);
    assertThat(posts.path("items")).hasSize(1);
    assertThat(posts.path("items").get(0).path("id").asLong()).isEqualTo(carrying);
    var postsOutside =
        step(
            "room-note-quoting-posts-outsider",
            "GET",
            "/api/v1/public/notes/" + roomNote + "/posts",
            outsider,
            null,
            200);
    assertThat(postsOutside.path("items")).isEmpty();

    var postQuotes =
        step(
            "room-post-note-quotes",
            "GET",
            "/api/v1/public/posts/" + roomPost + "/quotes",
            owner,
            null,
            200);
    assertThat(postQuotes.path("total").asLong()).isEqualTo(1);
    assertThat(postQuotes.path("items").get(0).path("id").asLong())
        .isEqualTo(ofPost.path("id").asLong());
    var postQuotesOutside =
        step(
            "room-post-note-quotes-outsider",
            "GET",
            "/api/v1/public/posts/" + roomPost + "/quotes",
            outsider,
            null,
            200);
    assertThat(postQuotesOutside.path("total").asLong()).isZero();
    assertThat(postQuotesOutside.path("items")).isEmpty();
  }

  private long room(Actor owner, Actor friend) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, '인용하는 방', NOW(6),"
            + " NOW(6))",
        owner.id());
    long roomId =
        jdbc.queryForObject("SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, owner.id());
    for (Actor member : List.of(owner, friend)) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          roomId,
          member.id());
    }
    return roomId;
  }

  private long roomNote(Actor author, long roomId) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, room_id)"
            + " VALUES (?, '방에서 인용될 노트', NOW(6), 'ROOM', ?)",
        author.id(),
        roomId);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.id());
  }

  private long roomPost(Actor author, long roomId, String status, List<String> embedded) {
    String slug = "room-" + status.toLowerCase(Locale.ROOT);
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, status, audience, room_id, published_at,"
            + " created_at, updated_at) VALUES (?, ?, '방 글', ?, 'ROOM', ?,"
            + " IF(? = 'PUBLISHED', NOW(6), NULL), NOW(6), NOW(6))",
        author.id(),
        slug,
        status,
        roomId,
        status);
    long postId =
        jdbc.queryForObject(
            "SELECT id FROM posts WHERE user_id = ? AND slug = ?", Long.class, author.id(), slug);
    for (int i = 0; i < embedded.size(); i++) {
      jdbc.update(
          "INSERT INTO post_block (post_id, block_type, content, block_order, created_at,"
              + " updated_at) VALUES (?, 'EMBED', ?, ?, NOW(6), NOW(6))",
          postId,
          embedded.get(i),
          i);
    }
    return postId;
  }

  private long told(Actor recipient, String type) {
    return count("notification", "recipient_user_id = ? AND type = ?", recipient.id(), type);
  }
}
