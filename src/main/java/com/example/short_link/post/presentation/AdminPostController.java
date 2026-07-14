package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.application.write.DeletePostUseCase;
import com.example.short_link.post.application.write.UnpublishPostUseCase;
import com.example.short_link.post.application.write.UpdatePostMetadataUseCase;
import com.example.short_link.post.presentation.request.AdminUpdatePostRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only post moderation. Mapped under {@code /api/v1/admin/**}, which the security layer gates
 * to the ADMIN role, so no per-method authorization is needed — the same convention {@code
 * AdminAbuseReportController} follows.
 */
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

  private final UnpublishPostUseCase unpublishPost;
  private final DeletePostUseCase deletePost;
  private final UpdatePostMetadataUseCase updatePostMetadata;

  /**
   * Takedown: unpublish any author's post. Idempotent — a post that is already unpublished is a
   * no-op; a missing post is a 404.
   */
  @PostMapping("/{id}/unpublish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unpublish(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
    unpublishPost.adminExecute(adminUserId, id);
  }

  /**
   * Moderation edit: rewrite any author's title and/or tags (PATCH semantics — null field is left
   * unchanged). Body, slug, cover and excerpt stay the author's; a missing post is a 404.
   */
  @PatchMapping("/{id}")
  public PostView update(
      @AuthenticationPrincipal Long adminUserId,
      @PathVariable Long id,
      @Valid @RequestBody AdminUpdatePostRequest request) {
    return PostView.from(
        updatePostMetadata.adminExecute(adminUserId, id, request.title(), request.tags()));
  }

  /**
   * Removal: permanently delete any author's post with the same cascade as the owner path (blocks,
   * revisions, comments, likes, bookmarks, highlights, reads, collection connections). A missing
   * post is a 404 — deletion is not idempotent on purpose, so a double-submit surfaces instead of
   * silently "succeeding" on nothing.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
    deletePost.adminExecute(adminUserId, id);
  }
}
