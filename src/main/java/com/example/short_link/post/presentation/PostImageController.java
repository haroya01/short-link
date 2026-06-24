package com.example.short_link.post.presentation;

import com.example.short_link.post.application.image.PostImageService;
import com.example.short_link.post.presentation.request.PostImageCommitRequest;
import com.example.short_link.post.presentation.request.PostImageImportRequest;
import com.example.short_link.post.presentation.request.PostImagePresignRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts/{postId}/images")
@RequiredArgsConstructor
public class PostImageController {

  private final PostImageService postImageService;

  @PostMapping("/presign")
  public PostImageService.PresignResult presign(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @Valid @RequestBody PostImagePresignRequest request) {
    return postImageService.presignUpload(userId, postId, request.contentType());
  }

  @PostMapping("/commit")
  public PostImageService.CommitResult commit(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @Valid @RequestBody PostImageCommitRequest request) {
    return postImageService.commitUpload(userId, postId, request.key());
  }

  /** Re-host an external image URL (e.g. pasted from Notion) — server fetches and stores it. */
  @PostMapping("/import")
  public PostImageService.CommitResult importFromUrl(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @Valid @RequestBody PostImageImportRequest request) {
    return postImageService.importFromUrl(userId, postId, request.url());
  }
}
