package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Two real users discover an author, react, discuss, save and remove their own reading data. */
class ReaderInteractionHttpQueryContractTest extends ContentHttpJourneySupport {

  @Test
  void readerFollowsDiscussesAndOrganizesAnAuthorsPublishedPost() throws Exception {
    long postId = createPost("interaction-post-create", "shared-story", "A shared story");
    step(
        "interaction-body-write",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", "A clear sentence worth remembering."),
        200);
    publish("interaction-post-publish", postId);
    followAuthor(postId);
    likeAndUnlike(postId);
    saveIntoFolder(postId);
    discussPost(postId);
    highlightAndReply(postId);
    recordAndClearHistory(postId);
    blockAndUnblock(postId);
    step("reader-unfollow-author", "DELETE", followPath(author), reader, null, 200);
    assertThat(
            count("user_follow", "follower_id = ? AND following_id = ?", reader.id(), author.id()))
        .isZero();
    deletePostWithLiveReaderData(postId);
    verifyContracts();
  }

  private void followAuthor(long postId) throws Exception {
    step(
        "reader-follow-author",
        "PUT",
        followPath(author) + "?sourcePostId=" + postId,
        reader,
        null,
        200);
    assertThat(
            count("user_follow", "follower_id = ? AND following_id = ?", reader.id(), author.id()))
        .isEqualTo(1);
    assertThat(
            get("reader-follow-status", followPath(author), reader).path("following").asBoolean())
        .isTrue();
    assertThat(
            get(
                    "reader-author-followers",
                    "/api/v1/users/" + author.username() + "/followers",
                    reader)
                .toString())
        .contains(reader.username());
    assertThat(
            get("reader-own-following", "/api/v1/users/" + reader.username() + "/following", reader)
                .toString())
        .contains(author.username());
    assertThat(
            get("reader-following-feed", "/api/v1/feed/following", reader)
                .path("items")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(postId);
    assertThat(count("notification", "recipient_user_id = ?", author.id())).isPositive();
  }

  private void likeAndUnlike(long postId) throws Exception {
    String path = postPath(postId) + "/like";
    step("reader-post-like", "PUT", path, reader, null, 200);
    step("reader-post-like-idempotent", "PUT", path, reader, null, 200);
    assertThat(count("post_like", "post_id = ? AND user_id = ?", postId, reader.id())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, postId))
        .isEqualTo(1);
    assertThat(get("reader-post-like-status", path, reader).path("liked").asBoolean()).isTrue();
    assertThat(get("reader-my-liked-posts", "/api/v1/users/me/likes", reader).toString())
        .contains("A shared story");
    step("reader-post-unlike", "DELETE", path, reader, null, 200);
    assertThat(count("post_like", "post_id = ? AND user_id = ?", postId, reader.id())).isZero();
    assertThat(jdbc.queryForObject("SELECT like_count FROM posts WHERE id = ?", Long.class, postId))
        .isZero();
  }

  private void saveIntoFolder(long postId) throws Exception {
    String path = postPath(postId) + "/bookmark";
    step("reader-bookmark-post", "PUT", path, reader, null, 200);
    assertThat(get("reader-bookmark-status", path, reader).path("bookmarked").asBoolean()).isTrue();
    assertThat(get("reader-bookmark-list", "/api/v1/bookmarks", reader).toString())
        .contains("A shared story");
    long folderId =
        step(
                "reader-bookmark-folder-create",
                "POST",
                "/api/v1/bookmarks/folders",
                reader,
                Map.of("name", "Architecture"),
                201)
            .path("id")
            .asLong();
    step(
        "reader-bookmark-file-in-folder",
        "PUT",
        "/api/v1/me/saved/" + postId + "/folder",
        reader,
        Map.of("folderId", folderId),
        204);
    assertThat(
            jdbc.queryForObject(
                "SELECT folder_id FROM post_bookmark WHERE post_id = ? AND user_id = ?",
                Long.class,
                postId,
                reader.id()))
        .isEqualTo(folderId);
    assertThat(get("reader-saved-library", "/api/v1/me/saved", reader).toString())
        .contains("A shared story");
    assertThat(
            get("reader-bookmark-folder-list", "/api/v1/bookmarks/folders", reader)
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(folderId);
    step(
        "reader-bookmark-folder-rename",
        "PATCH",
        "/api/v1/bookmarks/folders/" + folderId,
        reader,
        Map.of("name", "Read again"),
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM bookmark_folder WHERE id = ?", String.class, folderId))
        .isEqualTo("Read again");
    step(
        "reader-foreign-bookmark-folder-denied",
        "PATCH",
        "/api/v1/bookmarks/folders/" + folderId,
        outsider,
        Map.of("name", "Other reader"),
        404);
    step(
        "reader-bookmark-folder-delete",
        "DELETE",
        "/api/v1/bookmarks/folders/" + folderId,
        reader,
        null,
        204);
    assertThat(count("bookmark_folder", "id = ?", folderId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT folder_id FROM post_bookmark WHERE post_id = ? AND user_id = ?",
                Long.class,
                postId,
                reader.id()))
        .isNull();
    step("reader-post-unbookmark", "DELETE", path, reader, null, 200);
    assertThat(count("post_bookmark", "post_id = ? AND user_id = ?", postId, reader.id())).isZero();
  }

  private void discussPost(long postId) throws Exception {
    long comment =
        step(
                "reader-comment-create",
                "POST",
                postPath(postId) + "/comments",
                reader,
                Map.of("body", "This explanation helped."),
                201)
            .path("id")
            .asLong();
    long reply =
        step(
                "author-comment-reply",
                "POST",
                postPath(postId) + "/comments",
                author,
                Map.of("body", "Thank you for reading.", "parentId", comment),
                201)
            .path("id")
            .asLong();
    assertThat(count("comment", "post_id = ?", postId)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT parent_id FROM comment WHERE id = ?", Long.class, reply))
        .isEqualTo(comment);
    assertThat(
            get(
                    "reader-public-comment-thread",
                    "/api/v1/public/posts/" + postId + "/comments",
                    null)
                .toString())
        .contains("This explanation helped.", "Thank you for reading.");
    step("author-comment-like", "POST", "/api/v1/comments/" + comment + "/like", author, null, 200);
    assertThat(count("comment_like", "comment_id = ? AND user_id = ?", comment, author.id()))
        .isEqualTo(1);
    assertThat(
            get("author-liked-comment-ids", postPath(postId) + "/comments/liked", author)
                .get(0)
                .asLong())
        .isEqualTo(comment);
    assertThat(get("reader-my-comment-history", "/api/v1/users/me/comments", reader).toString())
        .contains("This explanation helped.");
    step(
        "outsider-comment-delete-denied",
        "DELETE",
        "/api/v1/comments/" + comment,
        outsider,
        null,
        403);
    step(
        "author-comment-unlike",
        "DELETE",
        "/api/v1/comments/" + comment + "/like",
        author,
        null,
        200);
    assertThat(count("comment_like", "comment_id = ?", comment)).isZero();
    step(
        "reader-comment-delete-with-replies",
        "DELETE",
        "/api/v1/comments/" + comment,
        reader,
        null,
        204);
    assertThat(count("comment", "post_id = ?", postId)).isZero();
  }

  private void highlightAndReply(long postId) throws Exception {
    long highlight =
        step(
                "reader-highlight-create",
                "POST",
                postPath(postId) + "/highlights",
                reader,
                Map.of(
                    "blockOrder",
                    0,
                    "startOffset",
                    0,
                    "endOffset",
                    16,
                    "quote",
                    "A clear sentence",
                    "note",
                    "Keep this principle"),
                201)
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT quote FROM post_highlight WHERE id = ?", String.class, highlight))
        .isEqualTo("A clear sentence");
    assertThat(
            get(
                    "reader-public-post-highlights",
                    "/api/v1/public/posts/" + postId + "/highlights",
                    null)
                .toString())
        .contains("A clear sentence");
    assertThat(get("reader-my-highlight-library", "/api/v1/users/me/highlights", reader).toString())
        .contains("Keep this principle");
    assertThat(
            get("reader-global-highlight-feed", "/api/v1/highlights/feed?scope=global", outsider)
                .toString())
        .contains("A clear sentence");
    step("author-follow-highlight-curator", "PUT", followPath(reader), author, null, 200);
    assertThat(
            get("author-followed-highlight-feed", "/api/v1/highlights/feed", author)
                .path("source")
                .asText())
        .isEqualTo("following");
    long reply =
        step(
                "author-highlight-reply-create",
                "POST",
                "/api/v1/highlights/" + highlight + "/replies",
                author,
                Map.of("body", "I will expand this idea."),
                201)
            .path("id")
            .asLong();
    assertThat(
            get(
                    "reader-public-highlight-replies",
                    "/api/v1/public/highlights/" + highlight + "/replies",
                    null)
                .toString())
        .contains("I will expand this idea.");
    step(
        "outsider-highlight-delete-denied",
        "DELETE",
        "/api/v1/highlights/" + highlight,
        outsider,
        null,
        403);
    step(
        "author-highlight-reply-delete",
        "DELETE",
        "/api/v1/highlight-replies/" + reply,
        author,
        null,
        204);
    assertThat(count("highlight_reply", "id = ?", reply)).isZero();
    step("reader-highlight-delete", "DELETE", "/api/v1/highlights/" + highlight, reader, null, 204);
    assertThat(count("post_highlight", "id = ?", highlight)).isZero();
  }

  private void recordAndClearHistory(long postId) throws Exception {
    step("reader-history-record", "POST", postPath(postId) + "/read", reader, null, 204);
    assertThat(count("post_read", "post_id = ? AND user_id = ?", postId, reader.id())).isEqualTo(1);
    assertThat(get("reader-reading-history", "/api/v1/users/me/reading-history", reader).toString())
        .contains("A shared story");
    step(
        "reader-history-forget-one",
        "DELETE",
        "/api/v1/users/me/reading-history/" + postId,
        reader,
        null,
        204);
    assertThat(count("post_read", "user_id = ?", reader.id())).isZero();
    step("reader-history-record-again", "POST", postPath(postId) + "/read", reader, null, 204);
    step(
        "reader-history-clear-all",
        "DELETE",
        "/api/v1/users/me/reading-history",
        reader,
        null,
        204);
    assertThat(count("post_read", "user_id = ?", reader.id())).isZero();
  }

  private void blockAndUnblock(long postId) throws Exception {
    String path = "/api/v1/users/" + outsider.username() + "/block";
    step("author-block-reader", "PUT", path, author, null, 204);
    assertThat(count("user_block", "blocker_id = ? AND blocked_id = ?", author.id(), outsider.id()))
        .isEqualTo(1);
    assertThat(get("author-blocked-users-list", "/api/v1/users/me/blocks", author).toString())
        .contains(outsider.username());
    step(
        "blocked-reader-comment-denied",
        "POST",
        postPath(postId) + "/comments",
        outsider,
        Map.of("body", "This must not be stored."),
        403);
    assertThat(count("comment", "post_id = ? AND user_id = ?", postId, outsider.id())).isZero();
    step("author-unblock-reader", "DELETE", path, author, null, 204);
    assertThat(count("user_block", "blocker_id = ? AND blocked_id = ?", author.id(), outsider.id()))
        .isZero();
  }

  private void deletePostWithLiveReaderData(long postId) throws Exception {
    LiveReaderData live = leaveReaderDataOnPost(postId);
    step("author-delete-post-with-live-reader-data", "DELETE", postPath(postId), author, null, 204);
    assertPostDependenciesRemoved(postId, live);
    assertThat(
            get(
                    "reader-collection-after-source-post-deletion",
                    "/api/v1/collections/" + live.collectionId(),
                    reader)
                .path("connections")
                .size())
        .isZero();
  }

  private LiveReaderData leaveReaderDataOnPost(long postId) throws Exception {
    step("removal-live-like", "PUT", postPath(postId) + "/like", reader, null, 200);
    step("removal-live-bookmark", "PUT", postPath(postId) + "/bookmark", reader, null, 200);
    step("removal-live-read", "POST", postPath(postId) + "/read", reader, null, 204);
    long comment =
        step(
                "removal-live-comment",
                "POST",
                postPath(postId) + "/comments",
                reader,
                Map.of("body", "This discussion is still present when the article is removed."),
                201)
            .path("id")
            .asLong();
    step(
        "removal-live-comment-like",
        "POST",
        "/api/v1/comments/" + comment + "/like",
        author,
        null,
        200);
    long highlight =
        step(
                "removal-live-highlight",
                "POST",
                postPath(postId) + "/highlights",
                reader,
                Map.of(
                    "blockOrder",
                    0,
                    "startOffset",
                    0,
                    "endOffset",
                    16,
                    "quote",
                    "A clear sentence"),
                201)
            .path("id")
            .asLong();
    long reply =
        step(
                "removal-live-highlight-reply",
                "POST",
                "/api/v1/highlights/" + highlight + "/replies",
                author,
                Map.of("body", "The reply must disappear with its quotation."),
                201)
            .path("id")
            .asLong();
    long collection =
        step(
                "removal-retained-reader-collection",
                "POST",
                "/api/v1/collections",
                reader,
                Map.of("title", "The readers own collection", "visibility", "PUBLIC"),
                201)
            .path("id")
            .asLong();
    step(
        "removal-live-post-connection",
        "POST",
        "/api/v1/collections/" + collection + "/connections",
        reader,
        Map.of("blockType", "POST", "refId", postId),
        201);
    step(
        "removal-live-highlight-connection",
        "POST",
        "/api/v1/collections/" + collection + "/connections",
        reader,
        Map.of("blockType", "HIGHLIGHT", "refId", highlight),
        201);
    for (String table :
        java.util.List.of("post_like", "post_bookmark", "post_read", "comment", "post_highlight")) {
      assertThat(count(table, "post_id = ?", postId))
          .as("Populated dependency %s before deletion", table)
          .isPositive();
    }
    assertThat(count("collection_connection", "collection_id = ?", collection)).isEqualTo(2);
    assertThat(count("comment_like", "comment_id = ?", comment)).isEqualTo(1);
    assertThat(count("highlight_reply", "id = ?", reply)).isEqualTo(1);

    return new LiveReaderData(comment, reply, collection);
  }

  private void assertPostDependenciesRemoved(long postId, LiveReaderData live) {
    for (String table :
        java.util.List.of(
            "post_like",
            "post_bookmark",
            "post_read",
            "comment",
            "post_highlight",
            "post_block",
            "post_revision",
            "post_search_text")) {
      assertThat(count(table, "post_id = ?", postId))
          .as("Removed dependency %s after deletion", table)
          .isZero();
    }
    assertThat(count("posts", "id = ?", postId)).isZero();
    assertThat(count("comment_like", "comment_id = ?", live.commentId())).isZero();
    assertThat(count("highlight_reply", "id = ?", live.replyId())).isZero();
    assertThat(count("collection_connection", "collection_id = ?", live.collectionId())).isZero();
    assertThat(count("collection", "id = ? AND owner_id = ?", live.collectionId(), reader.id()))
        .isEqualTo(1);
  }

  private record LiveReaderData(long commentId, long replyId, long collectionId) {}

  private static String followPath(Actor person) {
    return "/api/v1/users/" + person.username() + "/follow";
  }
}
