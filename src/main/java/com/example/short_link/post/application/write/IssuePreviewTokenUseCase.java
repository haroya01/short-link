package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues (get-or-create) the share token for an owned post, so the owner can preview a
 * not-yet-public draft via an unguessable link. Idempotent: re-opening the share dialog returns the
 * same token, so the link stays stable.
 */
@Service
@RequiredArgsConstructor
public class IssuePreviewTokenUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;

  @Transactional
  public String issue(Long userId, Long postId) {
    PostEntity post = postOwnership.requireOwned(userId, postId);
    String token = post.ensurePreviewToken(newToken());
    postRepository.save(post);
    return token;
  }

  private static String newToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
