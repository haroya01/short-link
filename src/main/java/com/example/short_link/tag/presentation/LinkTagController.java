package com.example.short_link.tag.presentation;

import com.example.short_link.tag.application.read.LinkTagQueryService;
import com.example.short_link.tag.application.write.ReplaceLinkTagsUseCase;
import com.example.short_link.tag.presentation.request.LinkTagsRequest;
import com.example.short_link.tag.presentation.response.LinkTagsResponse;
import jakarta.validation.Valid;
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

  private final LinkTagQueryService queryService;
  private final ReplaceLinkTagsUseCase replaceTags;

  @GetMapping("/{shortCode}/tags")
  public LinkTagsResponse tags(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    return new LinkTagsResponse(shortCode, queryService.tagNamesFor(userId, shortCode));
  }

  @PutMapping("/{shortCode}/tags")
  public LinkTagsResponse replace(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @Valid @RequestBody LinkTagsRequest request) {
    return new LinkTagsResponse(shortCode, replaceTags.execute(userId, shortCode, request.tags()));
  }
}
