package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated read of a post by its share token — lets the owner show a not-yet-public draft
 * via an unguessable link. The token is the authorization, so there's no username in the path. The
 * Next.js post page calls this when the URL carries {@code ?preview=...} and renders the result
 * noindex.
 */
@RestController
@RequestMapping("/api/v1/public/preview")
@RequiredArgsConstructor
public class PublicPreviewController {

  private final PublicPostQueryService publicPostQueryService;

  @GetMapping("/{token}")
  public PublicPostDetail preview(@PathVariable String token) {
    return publicPostQueryService.findPreviewPost(token);
  }
}
