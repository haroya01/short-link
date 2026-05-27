package com.example.short_link.profile.presentation;

import com.example.short_link.profile.application.image.ProfileImageService;
import com.example.short_link.profile.presentation.request.ProfileImageCommitRequest;
import com.example.short_link.profile.presentation.request.ProfileImagePresignRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for uploading profile-block images (gallery, product card, etc.) directly to S3 via
 * presigned PUT URLs. The returned {@code imageUrl} is meant to be embedded into a block's JSON
 * content through the existing block update endpoint — this controller does not write any block
 * itself.
 */
@RestController
@RequestMapping("/api/v1/users/me/profile/images")
@RequiredArgsConstructor
public class ProfileImageController {

  private final ProfileImageService service;

  @PostMapping("/presigned-url")
  public ProfileImageService.PresignResult presign(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody ProfileImagePresignRequest request) {
    return service.presignUpload(userId, request.contentType());
  }

  @PutMapping
  public ProfileImageService.CommitResult commit(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ProfileImageCommitRequest request) {
    return service.commitUpload(userId, request.key());
  }
}
