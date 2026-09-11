package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다시 요청해도 기존 토큰을 유지해 공유 URL이 바뀌지 않게 한다. */
@Service
@RequiredArgsConstructor
public class IssuePreviewTokenUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;

  @Transactional
  public String issue(Long userId, Long postId) {
    PostEntity post = postOwnership.requireOwnedForUpdate(userId, postId);
    String token = post.ensurePreviewToken(newToken());
    postRepository.save(post);
    return token;
  }

  private static String newToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
