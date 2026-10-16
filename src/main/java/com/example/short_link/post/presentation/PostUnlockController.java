package com.example.short_link.post.presentation;

import com.example.short_link.post.application.access.PostGrants;
import com.example.short_link.post.application.write.UnlockPostUseCase;
import com.example.short_link.post.presentation.request.UnlockPostRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PostUnlockController {

  private final UnlockPostUseCase unlockPost;

  @PostMapping("/{username}/posts/{slug}/unlock")
  public PostGrants.Grant unlock(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @PathVariable String slug,
      @Valid @RequestBody UnlockPostRequest request,
      HttpServletRequest req) {
    return unlockPost.unlock(
        username, slug, request.password(), PostReaderHeaders.from(viewerId, req));
  }
}
