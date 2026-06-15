package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostLikeRepository;
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
  private final CommentRepository commentRepository;
  private final PostLikeRepository postLikeRepository;
  private final PostBookmarkRepository postBookmarkRepository;
  private final PostHighlightRepository postHighlightRepository;
  private final ProfileCacheInvalidator cacheEviction;

  /**
   * 본인 글을 영구 삭제. block → revision 본문 데이터에 더해, 같은 글에 달린 댓글 (PII 포함) · 좋아요 · 북마크 · 하이라이트 같은 상호작용 행도
   * 함께 정리해 고아 데이터를 남기지 않는다. 마지막에 post 를 지운다. 분석 데이터 (click_event 등) 는 별도 도메인이라 영향 없음.
   */
  @Transactional
  public void execute(DeletePostCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
    postBlockRepository.deleteAllByPostId(post.getId());
    postRevisionRepository.deleteAllByPostId(post.getId());
    commentRepository.deleteAllByPostId(post.getId());
    postLikeRepository.deleteAllByPostId(post.getId());
    postBookmarkRepository.deleteAllByPostId(post.getId());
    postHighlightRepository.deleteAllByPostId(post.getId());
    postRepository.delete(post);
    // Deleting a published post drops the author's count; evict so a now-empty blog hides its
    // entry-point on the public profile.
    cacheEviction.evictByUserId(post.getUserId());
  }
}
