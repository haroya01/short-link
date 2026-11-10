package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.read.AdminLinkPreviewService;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/links")
@RequiredArgsConstructor
public class AdminLinkPreviewController {

  private final AdminLinkPreviewService previews;

  @GetMapping("/{code}/preview-image")
  public ResponseEntity<byte[]> previewImage(
      @PathVariable ShortCode code,
      @RequestParam(defaultValue = "false") boolean clear,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore())
        .body(previews.preview(code, clear, userId));
  }
}
