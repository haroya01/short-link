package com.example.short_link.user.api;

import com.example.short_link.user.application.avatar.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/avatar")
@RequiredArgsConstructor
public class AvatarController {

  private final AvatarService service;

  @PostMapping("/presigned-url")
  public AvatarService.PresignResult presign(
      @AuthenticationPrincipal Long userId, @RequestBody PresignRequest request) {
    return service.presignUpload(userId, request.contentType());
  }

  @PutMapping
  public AvatarService.CommitResult commit(
      @AuthenticationPrincipal Long userId, @RequestBody CommitRequest request) {
    return service.commitUpload(userId, request.key());
  }

  @DeleteMapping
  public void clear(@AuthenticationPrincipal Long userId) {
    service.clearAvatar(userId);
  }

  public record PresignRequest(String contentType) {}

  public record CommitRequest(String key) {}
}
