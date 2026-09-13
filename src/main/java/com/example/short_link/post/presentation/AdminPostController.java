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

/** The security layer restricts {@code /api/v1/admin/**} to ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

  private final UnpublishPostUseCase unpublishPost;
  private final DeletePostUseCase deletePost;
  private final UpdatePostMetadataUseCase updatePostMetadata;

  @PostMapping("/{id}/unpublish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unpublish(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
    unpublishPost.adminExecute(adminUserId, id);
  }

  /** Null fields are unchanged. Moderation edits only title/tags; a missing post returns 404. */
  @PatchMapping("/{id}")
  public PostView update(
      @AuthenticationPrincipal Long adminUserId,
      @PathVariable Long id,
      @Valid @RequestBody AdminUpdatePostRequest request) {
    return updatePostMetadata.adminExecute(adminUserId, id, request.title(), request.tags());
  }

  /** Permanent deletion. Missing posts return 404, including repeated deletion requests. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long adminUserId, @PathVariable Long id) {
    deletePost.adminExecute(adminUserId, id);
  }
}
