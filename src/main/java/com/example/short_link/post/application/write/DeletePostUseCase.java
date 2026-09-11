package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
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
  private final PostReadRepository postReadRepository;
  private final ProfileCacheInvalidator cacheEviction;
  private final CollectionConnectionCleaner connectionCleaner;

  /** 컬렉션 연결에는 FK가 없으므로 하이라이트 ID를 삭제 전에 읽어 연결도 제거한다. */
  @Transactional
  public void execute(DeletePostCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    deleteCascade(post);
  }

  /** 관리자 권한은 HTTP 보안 계층에서 검사한다. adminUserId는 감사 로그용이다. */
  @Transactional
  public void adminExecute(Long adminUserId, Long postId) {
    log.info("admin post delete: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post =
        postRepository
            .findByIdForUpdate(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    deleteCascade(post);
  }

  private void deleteCascade(PostEntity post) {
    List<Long> highlightIds =
        postHighlightRepository
            .findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(post.getId())
            .stream()
            .map(PostHighlightEntity::getId)
            .toList();
    postBlockRepository.deleteAllByPostId(post.getId());
    postRevisionRepository.deleteAllByPostId(post.getId());
    commentRepository.deleteAllByPostId(post.getId());
    postLikeRepository.deleteAllByPostId(post.getId());
    postBookmarkRepository.deleteAllByPostId(post.getId());
    connectionCleaner.purgeForHighlights(highlightIds);
    postHighlightRepository.deleteAllByPostId(post.getId());
    postReadRepository.deleteAllByPostId(post.getId());
    connectionCleaner.purgeForPost(post.getId());
    postRepository.delete(post);
    // 마지막 공개 글을 삭제하면 프로필의 블로그 진입점도 사라져야 한다.
    cacheEviction.evictByUserId(post.getUserId());
  }
}
