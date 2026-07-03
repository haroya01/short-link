package com.example.short_link.post.presentation;

import com.example.short_link.post.application.write.UnpublishPostUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  /**
   * Takedown: unpublish any author's post. Idempotent — a post that is already unpublished is a
   * no-op; a missing post is a 404.
   */
  @PostMapping("/{id}/unpublish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unpublish(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
    unpublishPost.adminExecute(adminUserId, id);
  }
}
