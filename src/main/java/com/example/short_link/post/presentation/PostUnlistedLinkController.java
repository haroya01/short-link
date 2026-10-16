package com.example.short_link.post.presentation;

import com.example.short_link.post.application.write.RotateUnlistedKeyUseCase;
import com.example.short_link.post.presentation.response.UnlistedLinkResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostUnlistedLinkController {

  private final RotateUnlistedKeyUseCase rotateUnlistedKey;

  @PostMapping("/{id}/unlisted-key/rotate")
  public UnlistedLinkResponse rotate(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return new UnlistedLinkResponse(rotateUnlistedKey.rotate(userId, id));
  }
}
