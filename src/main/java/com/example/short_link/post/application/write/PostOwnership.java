package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostOwnership {

  private final PostRepository postRepository;

  public PostEntity requireOwned(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    // open-in-view=false(#574) 이후 영속성 컨텍스트는 @Transactional 경계에서 닫힌다. 쓰기 유스케이스는
    // 이 엔티티를 그대로 반환하고 컨트롤러가 트랜잭션 밖에서 PostView.from() 으로 매핑하는데, 그때
    // lazy 인 tags(@ElementCollection)를 건드리면 LazyInitializationException 이 난다(발행 시 재현).
    // 소유자 조회는 항상 트랜잭션 안에서 일어나므로, 여기서 미리 초기화해 둬야 안전하다.
    // (공개 피드/상세 조회는 @Transactional 안에서 PostView 를 만들므로 영향 없다.)
    Hibernate.initialize(post.getTags());
    return post;
  }
}
