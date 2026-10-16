package com.example.short_link.post.application.write;

import com.example.short_link.post.application.access.PostUnlistedUrls;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RotateUnlistedKeyUseCase {

  private final PostOwnership postOwnership;
  private final PostAccessSettings accessSettings;
  private final PostRepository postRepository;
  private final PostUnlistedUrls unlistedUrls;

  @Transactional
  public String rotate(Long userId, Long postId) {
    PostEntity post = postOwnership.requireOwnedForUpdate(userId, postId);
    accessSettings.rotateUnlistedKey(post);
    postRepository.save(post);
    return unlistedUrls.of(post);
  }
}
