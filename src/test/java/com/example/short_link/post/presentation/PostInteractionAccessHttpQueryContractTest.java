package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PostInteractionAccessHttpQueryContractTest extends ContentHttpJourneySupport {

  @Test
  void interactionWritesRespectThePostAuthorAccountAndPublicationState() throws Exception {
    Discussion discussion = publishDiscussion();
    long existingReply = leaveExistingInteractions(discussion);
    InteractionCounts existing = interactionCounts(discussion);
    assertThat(existing.postLikes()).isEqualTo(1);
    assertThat(existing.postLikeCounter()).isEqualTo(1);
    assertThat(existing.commentLikes()).isEqualTo(1);
    assertThat(existing.comments()).isEqualTo(1);
    assertThat(existing.highlights()).isEqualTo(1);
    assertThat(existing.replies()).isEqualTo(1);
    assertThat(existing.notifications()).isPositive();

    rejectBlockedReader(discussion, existing);
    rejectModeratedReader(discussion, existing);
    rejectInteractionsOnDeletedComment(discussion, existing);
    rejectInteractionsOnUnpublishedPost(discussion, existing);
    removeExistingInteractionsDespiteRestrictions(discussion, existingReply, existing);

    verifyContracts();
  }

  private Discussion publishDiscussion() throws Exception {
    long postId =
        createPost(
            "interaction-access-post-create", "protected-discussion", "A protected discussion");
    step(
        "interaction-access-body-write",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", "A clear sentence worth remembering."),
        200);
    publish("interaction-access-post-publish", postId);
    long highlightId =
        step(
                "interaction-access-highlight-create",
                "POST",
                postPath(postId) + "/highlights",
                reader,
                Map.of(
                    "blockOrder", 0,
                    "startOffset", 0,
                    "endOffset", 16,
                    "quote", "A clear sentence"),
                201)
            .path("id")
            .asLong();
    long commentId =
        step(
                "interaction-access-comment-create",
                "POST",
                postPath(postId) + "/comments",
                reader,
                Map.of("body", "This sentence is worth discussing."),
                201)
            .path("id")
            .asLong();
    assertThat(count("post_highlight", "id = ? AND post_id = ?", highlightId, postId)).isEqualTo(1);
    assertThat(count("comment", "id = ? AND post_id = ?", commentId, postId)).isEqualTo(1);
    return new Discussion(postId, highlightId, commentId);
  }

  private long leaveExistingInteractions(Discussion discussion) throws Exception {
    step(
        "interaction-access-existing-post-like",
        "PUT",
        postPath(discussion.postId()) + "/like",
        outsider,
        null,
        200);
    step(
        "interaction-access-existing-comment-like",
        "POST",
        commentLikePath(discussion),
        outsider,
        null,
        200);
    return step(
            "interaction-access-existing-reply",
            "POST",
            replyPath(discussion),
            outsider,
            Map.of("body", "I agree with this interpretation."),
            201)
        .path("id")
        .asLong();
  }

  private void rejectBlockedReader(Discussion discussion, InteractionCounts existing)
      throws Exception {
    step(
        "interaction-access-author-block",
        "PUT",
        "/api/v1/users/" + reader.username() + "/block",
        author,
        null,
        204);
    assertThat(count("user_block", "blocker_id = ? AND blocked_id = ?", author.id(), reader.id()))
        .isEqualTo(1);

    denyReply("interaction-access-blocked-reply", discussion, "COMMENT_BLOCKED", 403, existing);
    denyHighlight(
        "interaction-access-blocked-highlight",
        discussion,
        "POST_INTERACTION_BLOCKED",
        403,
        existing);
    denyPostLike(
        "interaction-access-blocked-post-like",
        discussion,
        "POST_INTERACTION_BLOCKED",
        403,
        existing);
    denyCommentLike(
        "interaction-access-blocked-comment-like",
        discussion,
        "POST_INTERACTION_BLOCKED",
        403,
        existing);
    deny(
        "interaction-access-blocked-comment",
        "POST",
        postPath(discussion.postId()) + "/comments",
        Map.of("body", "This blocked comment must not be stored."),
        "COMMENT_BLOCKED",
        403,
        discussion,
        existing);

    // Remove only the block fixture so the next requests exercise account moderation on its own.
    assertThat(
            jdbc.update(
                "DELETE FROM user_block WHERE blocker_id = ? AND blocked_id = ?",
                author.id(),
                reader.id()))
        .isEqualTo(1);
  }

  private void rejectModeratedReader(Discussion discussion, InteractionCounts existing)
      throws Exception {
    setModeration(reader, "BANNED");
    denyPostLike(
        "interaction-access-banned-post-like", discussion, "ACCOUNT_BANNED", 403, existing);

    setModeration(reader, "SUSPENDED");
    denyReply("interaction-access-suspended-reply", discussion, "ACCOUNT_SUSPENDED", 403, existing);
    denyHighlight(
        "interaction-access-suspended-highlight", discussion, "ACCOUNT_SUSPENDED", 403, existing);
    denyPostLike(
        "interaction-access-suspended-post-like", discussion, "ACCOUNT_SUSPENDED", 403, existing);
    denyCommentLike(
        "interaction-access-suspended-comment-like",
        discussion,
        "ACCOUNT_SUSPENDED",
        403,
        existing);
    setModeration(reader, "ACTIVE");
  }

  private void rejectInteractionsOnDeletedComment(Discussion discussion, InteractionCounts existing)
      throws Exception {
    // Admin soft deletion is a committed database fixture, outside the measured HTTP request.
    assertThat(
            jdbc.update(
                "UPDATE comment SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                discussion.commentId()))
        .isEqualTo(1);
    denyCommentLike(
        "interaction-access-deleted-comment-like", discussion, "COMMENT_NOT_FOUND", 404, existing);
    deny(
        "interaction-access-deleted-parent-comment-reply",
        "POST",
        postPath(discussion.postId()) + "/comments",
        Map.of(
            "body",
            "This reply to a deleted parent must not be stored.",
            "parentId",
            discussion.commentId()),
        "COMMENT_PARENT_INVALID",
        400,
        discussion,
        existing);
    assertThat(
            jdbc.update(
                "UPDATE comment SET deleted_at = NULL WHERE id = ?", discussion.commentId()))
        .isEqualTo(1);
  }

  private void rejectInteractionsOnUnpublishedPost(
      Discussion discussion, InteractionCounts existing) throws Exception {
    step(
        "interaction-access-post-unpublish",
        "POST",
        postPath(discussion.postId()) + "/unpublish",
        author,
        null,
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, discussion.postId()))
        .isEqualTo("UNPUBLISHED");
    denyReply("interaction-access-unpublished-reply", discussion, "POST_NOT_FOUND", 404, existing);
    denyHighlight(
        "interaction-access-unpublished-highlight", discussion, "POST_NOT_FOUND", 404, existing);
    denyPostLike(
        "interaction-access-unpublished-post-like", discussion, "POST_NOT_FOUND", 404, existing);
    denyCommentLike(
        "interaction-access-unpublished-comment-like", discussion, "POST_NOT_FOUND", 404, existing);
  }

  private void removeExistingInteractionsDespiteRestrictions(
      Discussion discussion, long replyId, InteractionCounts existing) throws Exception {
    // 차단·정지·비공개 상태에서도 자신이 남긴 데이터는 삭제할 수 있어야 한다.
    assertThat(
            jdbc.update(
                """
                INSERT INTO user_block (blocker_id, blocked_id, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP(6))
                """,
                author.id(),
                outsider.id()))
        .isEqualTo(1);
    setModeration(outsider, "SUSPENDED");

    step(
        "interaction-access-restricted-post-unlike",
        "DELETE",
        postPath(discussion.postId()) + "/like",
        outsider,
        null,
        200);
    assertThat(count("post_like", "post_id = ?", discussion.postId())).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Long.class, discussion.postId()))
        .isZero();
    step(
        "interaction-access-restricted-comment-unlike",
        "DELETE",
        commentLikePath(discussion),
        outsider,
        null,
        200);
    assertThat(count("comment_like", "comment_id = ?", discussion.commentId())).isZero();
    step(
        "interaction-access-restricted-reply-delete",
        "DELETE",
        "/api/v1/highlight-replies/" + replyId,
        outsider,
        null,
        204);
    assertThat(count("highlight_reply", "highlight_id = ?", discussion.highlightId())).isZero();
    assertThat(count("comment", "post_id = ?", discussion.postId())).isEqualTo(existing.comments());
    assertThat(count("post_highlight", "post_id = ?", discussion.postId()))
        .isEqualTo(existing.highlights());
    assertThat(notificationCount()).isEqualTo(existing.notifications());
  }

  private void setModeration(Actor actor, String status) {
    // Moderation is explicit fixture setup; the HTTP request still reads the real user's DB row.
    assertThat(
            jdbc.update(
                """
                UPDATE users SET moderation_status = ?,
                  suspended_until = CASE WHEN ? = 'SUSPENDED'
                    THEN DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY) ELSE NULL END
                WHERE id = ?
                """,
                status,
                status,
                actor.id()))
        .isEqualTo(1);
  }

  private void denyReply(
      String id, Discussion discussion, String errorCode, int status, InteractionCounts existing)
      throws Exception {
    deny(
        id,
        "POST",
        replyPath(discussion),
        Map.of("body", "This reply must not be stored."),
        errorCode,
        status,
        discussion,
        existing);
  }

  private void denyHighlight(
      String id, Discussion discussion, String errorCode, int status, InteractionCounts existing)
      throws Exception {
    // A different valid span ensures that the existing highlight's unique key cannot reject this.
    deny(
        id,
        "POST",
        postPath(discussion.postId()) + "/highlights",
        Map.of(
            "blockOrder", 0,
            "startOffset", 17,
            "endOffset", 34,
            "quote", "worth remembering"),
        errorCode,
        status,
        discussion,
        existing);
  }

  private void denyPostLike(
      String id, Discussion discussion, String errorCode, int status, InteractionCounts existing)
      throws Exception {
    deny(
        id,
        "PUT",
        postPath(discussion.postId()) + "/like",
        null,
        errorCode,
        status,
        discussion,
        existing);
  }

  private void denyCommentLike(
      String id, Discussion discussion, String errorCode, int status, InteractionCounts existing)
      throws Exception {
    deny(id, "POST", commentLikePath(discussion), null, errorCode, status, discussion, existing);
  }

  private void deny(
      String id,
      String method,
      String path,
      Object body,
      String errorCode,
      int status,
      Discussion discussion,
      InteractionCounts existing)
      throws Exception {
    assertThat(step(id, method, path, reader, body, status).path("code").asText())
        .isEqualTo(errorCode);
    assertThat(interactionCounts(discussion))
        .as("%s leaves database state unchanged", id)
        .isEqualTo(existing);
  }

  private InteractionCounts interactionCounts(Discussion discussion) {
    return new InteractionCounts(
        count("post_like", "post_id = ?", discussion.postId()),
        count("comment_like", "comment_id = ?", discussion.commentId()),
        count("comment", "post_id = ?", discussion.postId()),
        count("post_highlight", "post_id = ?", discussion.postId()),
        count("highlight_reply", "highlight_id = ?", discussion.highlightId()),
        notificationCount(),
        jdbc.queryForObject(
            "SELECT like_count FROM posts WHERE id = ?", Long.class, discussion.postId()));
  }

  private long notificationCount() {
    return count(
        "notification", "recipient_user_id IN (?, ?, ?)", author.id(), reader.id(), outsider.id());
  }

  private static String replyPath(Discussion discussion) {
    return "/api/v1/highlights/" + discussion.highlightId() + "/replies";
  }

  private static String commentLikePath(Discussion discussion) {
    return "/api/v1/comments/" + discussion.commentId() + "/like";
  }

  private record Discussion(long postId, long highlightId, long commentId) {}

  private record InteractionCounts(
      long postLikes,
      long commentLikes,
      long comments,
      long highlights,
      long replies,
      long notifications,
      long postLikeCounter) {}
}
