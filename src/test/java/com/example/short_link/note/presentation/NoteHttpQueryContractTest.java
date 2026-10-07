package com.example.short_link.note.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class NoteHttpQueryContractTest extends OperationalHttpJourneySupport {

  @MockitoBean private ObjectStorage objectStorage;

  @Test
  void repostsByPeopleYouFollowFlowIntoTheFollowingFeedOnceButNeverFromSomeoneYouBlocked()
      throws Exception {
    Actor reader = actor("feed-reader", false);
    Actor friend = actor("feed-friend", false);
    Actor stranger = actor("feed-stranger", false);
    Actor blocked = actor("feed-blocked", false);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        friend.id());
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        blocked.id());
    long friendNote = noteAt(friend, "친구가 쓴 노트", 10);
    long strangerNote = noteAt(stranger, "모르는 사람의 노트", 9);
    long blockedNote = noteAt(blocked, "차단한 사람의 노트", 8);
    repostAt(friend, strangerNote, 3);
    repostAt(friend, blockedNote, 2);
    repostAt(friend, friendNote, 1);

    var feed = step("note-following-reposts", "GET", "/api/v1/notes/following", reader, null, 200);

    List<Long> ids = new ArrayList<>();
    feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    assertThat(ids).containsExactly(friendNote, strangerNote);
    assertThat(feed.path("items").get(0).path("repostedBy").path("username").asText())
        .isEqualTo(friend.username());
    assertThat(feed.path("items").get(1).path("repostedBy").path("username").asText())
        .isEqualTo(friend.username());
    assertThat(feed.path("items").get(1).path("author").path("username").asText())
        .isEqualTo(stranger.username());
  }

  @Test
  void aReaderHidesOnePersonsRepostsThenEveryRepostInTheFollowingFeed() throws Exception {
    Actor reader = actor("hide-reader", false);
    Actor loud = actor("hide-loud", false);
    Actor quiet = actor("hide-quiet", false);
    Actor stranger = actor("hide-stranger", false);
    for (Actor followed : List.of(loud, quiet)) {
      jdbc.update(
          "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
          reader.id(),
          followed.id());
    }
    long loudNote = noteAt(loud, "시끄러운 사람의 노트", 10);
    long boostedByLoud = noteAt(stranger, "시끄러운 사람이 퍼간 노트", 9);
    long boostedByQuiet = noteAt(stranger, "조용한 사람이 퍼간 노트", 8);
    repostAt(loud, boostedByLoud, 2);
    repostAt(quiet, boostedByQuiet, 1);

    var hidden =
        step(
            "note-hide-reposts",
            "PUT",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(hidden.path("hidden").asBoolean()).isTrue();
    assertThat(count("note_repost_mute", "user_id = ?", reader.id())).isEqualTo(1);

    var visibility =
        step(
            "note-repost-visibility",
            "GET",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(visibility.path("hidden").asBoolean()).isTrue();

    var withoutLoud =
        step("note-following-hidden-reposts", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(withoutLoud)).containsExactly(boostedByQuiet, loudNote);

    var off =
        step(
            "note-feed-preferences-update",
            "PUT",
            "/api/v1/notes/feed-preferences",
            reader,
            Map.of("showReposts", false),
            200);
    assertThat(off.path("showReposts").asBoolean()).isFalse();

    var preferences =
        step("note-feed-preferences", "GET", "/api/v1/notes/feed-preferences", reader, null, 200);
    assertThat(preferences.path("showReposts").asBoolean()).isFalse();

    var originalsOnly =
        step("note-following-no-reposts", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(originalsOnly)).containsExactly(loudNote);

    var shown =
        step(
            "note-show-reposts",
            "DELETE",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(shown.path("hidden").asBoolean()).isFalse();
    assertThat(count("note_repost_mute", "user_id = ?", reader.id())).isZero();
  }

  @Test
  void hashtagsFileANoteUnderItsTagsAndAFollowedTagBringsItIntoTheFollowingFeed() throws Exception {
    Actor writer = actor("tag-writer", false);
    Actor reader = actor("tag-reader", false);
    jdbc.update(
        "INSERT INTO user_tag_pref (user_id, tag, kind, created_at)"
            + " VALUES (?, '스프링', 'FOLLOW', NOW(6))",
        reader.id());

    long noteId =
        step(
                "note-create-tagged",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "오늘 #스프링 정리, #Kotlin 도 #kotlin 조금"),
                201)
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForList(
                "SELECT tag FROM note_tag WHERE note_id = ? ORDER BY tag", String.class, noteId))
        .containsExactlyInAnyOrder("스프링", "Kotlin");

    var tagged =
        step(
            "note-tagged",
            "GET",
            "/api/v1/public/notes/tags/%EC%8A%A4%ED%94%84%EB%A7%81",
            null,
            null,
            200);
    assertThat(ids(tagged)).containsExactly(noteId);

    var following =
        step("note-following-tags", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(following)).containsExactly(noteId);
    assertThat(following.path("items").get(0).path("repostedBy").isNull()).isTrue();

    step(
        "note-edit-retag",
        "PATCH",
        "/api/v1/notes/" + noteId,
        writer,
        Map.of("body", "#Kotlin 만 남김"),
        200);
    assertThat(
            jdbc.queryForList("SELECT tag FROM note_tag WHERE note_id = ?", String.class, noteId))
        .containsExactly("Kotlin");
  }

  @Test
  void aMentionedMemberIsToldAndTheMentionLinksOnlyToMembersWhoExist() throws Exception {
    Actor writer = actor("mention-writer", false);
    Actor reader = actor("mention-reader", false);
    String handle = "mr" + reader.id();
    jdbc.update("UPDATE users SET username = ? WHERE id = ?", handle, reader.id());

    var created =
        step(
            "note-create-mention",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of("body", "@" + handle + " 이거 봐요, @nobody_here 는 없는 사람"),
            201);
    long noteId = created.path("id").asLong();
    assertThat(created.path("mentions").get(0).asText()).isEqualTo(handle);
    assertThat(created.path("mentions")).hasSize(1);

    var thread =
        step("note-thread-mention", "GET", "/api/v1/public/notes/" + noteId, null, null, 200);
    assertThat(thread.path("note").path("mentions").get(0).asText()).isEqualTo(handle);
    assertThat(
            count("notification", "recipient_user_id = ? AND type = 'NOTE_MENTION'", reader.id()))
        .isEqualTo(1);
  }

  @Test
  void aContentWarningIsStoredWithTheNoteAndCostsNoQuery() throws Exception {
    Actor writer = actor("warn-writer", false);

    var created =
        step(
            "note-create-warning",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of("body", "결말 이야기", "contentWarning", "스포일러", "sensitive", false),
            201);
    long noteId = created.path("id").asLong();
    assertThat(created.path("contentWarning").asText()).isEqualTo("스포일러");
    assertThat(created.path("sensitive").asBoolean()).isTrue();

    var edited =
        step(
            "note-edit-warning",
            "PATCH",
            "/api/v1/notes/" + noteId,
            writer,
            Map.of("body", "결말 이야기", "contentWarning", ""),
            200);
    assertThat(edited.path("contentWarning").isNull()).isTrue();

    var history =
        step("note-history", "GET", "/api/v1/public/notes/" + noteId + "/history", null, null, 200);
    assertThat(history.path("versions")).hasSize(2);
    assertThat(history.path("versions").get(0).path("contentWarning").isNull()).isTrue();
    assertThat(history.path("versions").get(1).path("contentWarning").asText()).isEqualTo("스포일러");
  }

  @Test
  void anAuthorPinsANoteToTheTopOfTheirProfile() throws Exception {
    Actor writer = actor("pin-writer", false);
    long older = noteAt(writer, "먼저 쓴 노트", 10);
    long newer = noteAt(writer, "나중에 쓴 노트", 1);

    var pinned = step("note-pin", "PUT", "/api/v1/notes/" + older + "/pin", writer, null, 200);
    assertThat(pinned.path("pinned").asBoolean()).isTrue();

    var profile =
        step(
            "note-profile-pinned",
            "GET",
            "/api/v1/public/profiles/" + writer.username() + "/notes",
            null,
            null,
            200);
    assertThat(ids(profile)).containsExactly(older, newer);
    assertThat(profile.path("items").get(0).path("pinned").asBoolean()).isTrue();

    var unpinned =
        step("note-unpin", "DELETE", "/api/v1/notes/" + older + "/pin", writer, null, 200);
    assertThat(unpinned.path("pinned").asBoolean()).isFalse();
  }

  private static List<Long> ids(JsonNode feed) {
    List<Long> ids = new ArrayList<>();
    feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  private long noteAt(Actor author, String body, int minutesAgo) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at)"
            + " VALUES (?, ?, NOW(6) - INTERVAL ? MINUTE)",
        author.id(),
        body,
        minutesAgo);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.id());
  }

  private void repostAt(Actor reposter, long noteId, int minutesAgo) {
    jdbc.update(
        "INSERT INTO note_repost (note_id, user_id, created_at)"
            + " VALUES (?, ?, NOW(6) - INTERVAL ? MINUTE)",
        noteId,
        reposter.id(),
        minutesAgo);
  }

  @Test
  void aWriterPostsRepliesQuotesEditsAndDeletesNotes() throws Exception {
    Actor writer = actor("note-writer", false);
    Actor reader = actor("note-reader", false);
    when(objectStorage.isConfigured()).thenReturn(true);
    when(objectStorage.presignPut(anyString(), anyString(), any()))
        .thenReturn("https://storage.example.test/upload");
    when(objectStorage.objectSize(anyString())).thenReturn(Optional.of(4L));
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, status, published_at, created_at, updated_at)"
            + " VALUES (?, 'quoted', 'Quoted essay', 'PUBLISHED', NOW(6), NOW(6), NOW(6))",
        writer.id());
    long postId =
        jdbc.queryForObject(
            "SELECT id FROM posts WHERE user_id = ? AND slug = 'quoted'", Long.class, writer.id());
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        writer.id());

    String key =
        step(
                "note-image-presign",
                "POST",
                "/api/v1/notes/images/presign",
                writer,
                Map.of("contentType", "image/png"),
                200)
            .path("key")
            .asText();
    assertThat(key).startsWith("note-images/" + writer.id() + "/").endsWith(".png");

    var note =
        step(
            "note-create",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of(
                "body",
                "첫 노트",
                "images",
                List.of(Map.of("key", key, "altText", "창밖 풍경")),
                "quotedPostId",
                postId),
            201);
    long noteId = note.path("id").asLong();
    assertThat(note.path("media").get(0).path("altText").asText()).isEqualTo("창밖 풍경");
    assertThat(note.path("quotedPost").path("slug").asText()).isEqualTo("quoted");
    assertThat(note.path("likeCount").asLong()).isZero();
    verify(objectStorage).applyImmutableCacheControl(key);

    long replyId =
        step(
                "note-reply",
                "POST",
                "/api/v1/notes",
                reader,
                Map.of("body", "답글", "inReplyToId", noteId),
                201)
            .path("id")
            .asLong();

    step("note-like", "PUT", "/api/v1/notes/" + noteId + "/like", reader, null, 200);

    var repost =
        step("note-repost", "PUT", "/api/v1/notes/" + noteId + "/repost", reader, null, 200);
    assertThat(repost.path("reposted").asBoolean()).isTrue();
    assertThat(repost.path("repostCount").asLong()).isEqualTo(1);

    var quote =
        step(
            "note-quote",
            "POST",
            "/api/v1/notes",
            reader,
            Map.of("body", "인용", "quotedNoteId", noteId),
            201);
    long quoteId = quote.path("id").asLong();
    assertThat(quote.path("quotedNote").path("id").asLong()).isEqualTo(noteId);
    assertThat(quote.path("quotedNote").path("author").path("username").asText())
        .isEqualTo(writer.username());
    assertThat(quote.path("quotedNote").path("media").get(0).path("altText").asText())
        .isEqualTo("창밖 풍경");

    var reposts =
        step(
            "note-reposts",
            "GET",
            "/api/v1/public/profiles/" + reader.username() + "/reposts",
            null,
            null,
            200);
    assertThat(reposts.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    assertThat(reposts.path("items").get(0).path("repostCount").asLong()).isEqualTo(1);

    var thread = step("note-thread", "GET", "/api/v1/public/notes/" + noteId, reader, null, 200);
    assertThat(thread.path("note").path("replyCount").asLong()).isEqualTo(1);
    assertThat(thread.path("note").path("likedByMe").asBoolean()).isTrue();
    assertThat(thread.path("note").path("likeCount").asLong()).isEqualTo(1);
    assertThat(thread.path("replies").get(0).path("id").asLong()).isEqualTo(replyId);
    assertThat(thread.path("note").path("quoteCount").asLong()).isEqualTo(1);

    var quotes =
        step("note-quotes", "GET", "/api/v1/public/notes/" + noteId + "/quotes", null, null, 200);
    assertThat(quotes.path("items").get(0).path("id").asLong()).isEqualTo(quoteId);

    var bookmark =
        step("note-bookmark", "PUT", "/api/v1/notes/" + noteId + "/bookmark", reader, null, 200);
    assertThat(bookmark.path("bookmarked").asBoolean()).isTrue();
    var saved = step("note-bookmarks", "GET", "/api/v1/notes/bookmarks", reader, null, 200);
    assertThat(saved.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    assertThat(saved.path("items").get(0).path("bookmarkedByMe").asBoolean()).isTrue();
    step("note-unbookmark", "DELETE", "/api/v1/notes/" + noteId + "/bookmark", reader, null, 200);
    assertThat(count("note_bookmark", "note_id = ?", noteId)).isZero();

    var mine =
        step(
            "note-profile",
            "GET",
            "/api/v1/public/profiles/" + writer.username() + "/notes",
            writer,
            null,
            200);
    assertThat(mine.path("items").get(0).path("likeCount").asLong()).isEqualTo(1);
    assertThat(mine.path("items").get(0).path("repostCount").asLong()).isEqualTo(1);

    var unrepost =
        step("note-unrepost", "DELETE", "/api/v1/notes/" + noteId + "/repost", reader, null, 200);
    assertThat(unrepost.path("reposted").asBoolean()).isFalse();
    assertThat(count("note_repost", "note_id = ?", noteId)).isZero();

    var following = step("note-following", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(following.path("items").get(0).path("id").asLong()).isEqualTo(quoteId);
    assertThat(following.path("items").get(1).path("id").asLong()).isEqualTo(noteId);

    var everyone = step("note-everyone", "GET", "/api/v1/public/notes", null, null, 200);
    assertThat(everyone.path("items").get(0).path("likeCount").asLong()).isZero();
    var trending =
        step("note-trending", "GET", "/api/v1/public/notes?sort=trending&size=50", null, null, 200);
    List<Long> ranked = new ArrayList<>();
    trending.path("items").forEach(item -> ranked.add(item.path("id").asLong()));
    assertThat(ranked).contains(noteId, quoteId).doesNotContain(replyId);
    assertThat(ranked.indexOf(noteId)).isLessThan(ranked.indexOf(quoteId));

    var edited =
        step("note-edit", "PATCH", "/api/v1/notes/" + noteId, writer, Map.of("body", "고친 노트"), 200);
    assertThat(edited.path("body").asText()).isEqualTo("고친 노트");
    assertThat(edited.path("editedAt").isNull()).isFalse();
    step(
        "note-edit-denied",
        "PATCH",
        "/api/v1/notes/" + noteId,
        reader,
        Map.of("body", "남의 노트"),
        403);

    step("note-delete", "DELETE", "/api/v1/notes/" + noteId, writer, null, 204);
    verify(objectStorage, timeout(5_000)).delete(key);
    assertThat(count("note_media", "note_id = ?", noteId)).isZero();
    assertThat(count("note", "id = ? AND in_reply_to_id IS NULL", replyId)).isEqualTo(1);
    assertThat(count("note", "id = ? AND quoted_note_id IS NULL", quoteId)).isEqualTo(1);
    step("note-thread-gone", "GET", "/api/v1/public/notes/" + noteId, null, null, 404);

    when(externalMetadata.fetch("https://example.com/essay"))
        .thenReturn(new OgMetadata("An essay", "Why links break", "https://example.com/cover.png"));
    long linkedId =
        step(
                "note-create-link",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "읽어 볼 글 https://example.com/essay."),
                201)
            .path("id")
            .asLong();
    var linked =
        step("note-thread-link", "GET", "/api/v1/public/notes/" + linkedId, null, null, 200);
    assertThat(linked.path("note").path("linkPreview").path("url").asText())
        .isEqualTo("https://example.com/essay");
    assertThat(linked.path("note").path("linkPreview").path("title").asText())
        .isEqualTo("An essay");
    assertThat(linked.path("note").path("linkPreview").path("image").asText())
        .isEqualTo("https://example.com/cover.png");
  }
}
