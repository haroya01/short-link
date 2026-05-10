package com.example.short_link.user.api;

import com.example.short_link.user.application.avatar.AvatarService;
import com.example.short_link.user.application.avatar.AvatarUnavailableException;
import com.example.short_link.user.application.avatar.InvalidAvatarException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

  @ExceptionHandler(InvalidAvatarException.class)
  public ResponseEntity<String> badRequest(InvalidAvatarException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(AvatarUnavailableException.class)
  public ResponseEntity<String> unavailable(AvatarUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(e.getMessage());
  }

  public record PresignRequest(String contentType) {}

  public record CommitRequest(String key) {}
}
