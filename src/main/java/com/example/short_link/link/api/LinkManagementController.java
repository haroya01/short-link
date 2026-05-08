package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkManagementService;
import com.example.short_link.link.application.MyLink;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkManagementController {

  private final LinkManagementService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @PatchMapping("/{shortCode}")
  public MyLinkResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @Valid @RequestBody UpdateLinkRequest request) {
    MyLink updated = service.update(userId, shortCode, request.originalUrl(), request.expiresAt());
    return new MyLinkResponse(
        updated.shortCode(),
        urlBuilder.build(updated.shortCode()),
        updated.originalUrl(),
        updated.createdAt(),
        updated.expiresAt(),
        updated.clickCount(),
        updated.tags());
  }

  @DeleteMapping("/{shortCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    service.delete(userId, shortCode);
  }

  @DeleteMapping
  public BulkDeleteResponse bulkDelete(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BulkDeleteRequest request) {
    int removed = service.bulkDelete(userId, request.shortCodes());
    return new BulkDeleteResponse(removed, request.shortCodes().size() - removed);
  }

  public record BulkDeleteResponse(int deleted, int skipped) {}
}
