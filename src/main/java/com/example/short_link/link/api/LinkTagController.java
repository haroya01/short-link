package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkTagService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkTagController {

  private final LinkTagService service;

  @GetMapping("/{shortCode}/tags")
  public LinkTagsResponse get(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    return new LinkTagsResponse(shortCode, service.tagNamesFor(userId, shortCode));
  }

  @PutMapping("/{shortCode}/tags")
  public LinkTagsResponse replace(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @Valid @RequestBody LinkTagsRequest request) {
    return new LinkTagsResponse(shortCode, service.replaceTags(userId, shortCode, request.tags()));
  }

  public record LinkTagsRequest(@Size(max = LinkTagService.MAX_TAGS_PER_LINK) List<String> tags) {}

  public record LinkTagsResponse(String shortCode, List<String> tags) {}
}
