package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.avatar.BannerService;
import com.example.short_link.user.presentation.request.BannerCommitRequest;
import com.example.short_link.user.presentation.request.BannerPresignRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/banner")
@RequiredArgsConstructor
public class BannerController {

  private final BannerService service;

  @PostMapping("/presigned-url")
  public BannerService.PresignResult presign(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BannerPresignRequest request) {
    return service.presignUpload(userId, request.contentType());
  }

  @PutMapping
  public BannerService.CommitResult commit(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BannerCommitRequest request) {
    return service.commitUpload(userId, request.key());
  }

  @DeleteMapping
  public void clear(@AuthenticationPrincipal Long userId) {
    service.clearBanner(userId);
  }
}
