package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletePostUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostBlockRepository postBlockRepository;
  private final PostRevisionRepository postRevisionRepository;
  private final ProfileCacheInvalidator cacheEviction;

  /**
   * 본인 글을 영구 삭제. FK 제약 회피 위해 block → revision → post 순서. 분석 데이터 (click_event 등) 는 별도 도메인이라 영향 없음.
   */
  @Transactional
  public void execute(DeletePostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    postBlockRepository.deleteAllByPostId(post.getId());
    postRevisionRepository.deleteAllByPostId(post.getId());
    postRepository.delete(post);
    // Deleting a published post drops the author's count; evict so a now-empty blog hides its
    // entry-point on the public profile.
    cacheEviction.evictByUserId(post.getUserId());
  }
}
