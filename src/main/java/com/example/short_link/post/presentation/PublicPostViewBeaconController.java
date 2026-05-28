package com.example.short_link.post.presentation;

import com.example.short_link.post.application.write.RecordPostViewCommand;
import com.example.short_link.post.application.write.RecordPostViewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public post 의 view beacon. 인증 없이 frontend 가 페이지 로드 시 fire. invalid username/slug 또는 non-PUBLISHED
 * 면 silent (UseCase 가 noop). dedup / bot 필터 없음 — v0 minimum, L3 engagement tracking 별도 트랙.
 */
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicPostViewBeaconController {

  private final RecordPostViewUseCase recordPostView;

  @PostMapping("/{username}/posts/{slug}/view")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void recordView(@PathVariable String username, @PathVariable String slug) {
    recordPostView.execute(new RecordPostViewCommand(username, slug));
  }
}
