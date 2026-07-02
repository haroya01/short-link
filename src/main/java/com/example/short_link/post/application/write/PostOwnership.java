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
    PostEntity post = loadOwned(userId, postId);
    // open-in-view=false(#574) 이후 영속성 컨텍스트는 @Transactional 경계에서 닫힌다. 쓰기 유스케이스는
    // 이 엔티티를 그대로 반환하고 컨트롤러가 트랜잭션 밖에서 PostView.from() 으로 매핑하는데, 그때
    // lazy 인 tags(@ElementCollection)를 건드리면 LazyInitializationException 이 난다(발행 시 재현).
    // 쓰기 유스케이스는 @Transactional 이라 여기서 미리 초기화해 두면 안전하다.
    // ※ 이 메서드는 반드시 @Transactional 안에서 불러야 한다 — 세션이 없으면 이 init 줄 자체가 터진다.
    //   엔티티를 매핑하지 않는 단순 권한 가드는 tags 가 필요 없으니 verifyOwned 를 쓸 것.
    Hibernate.initialize(post.getTags());
    return post;
  }

  /**
   * 소유권만 검증한다(엔티티 반환 없음). {@link #requireOwned} 와 달리 lazy tags 를 건드리지 않으므로, 글을 매핑하지 않고 권한 가드로만 쓰는
   * 호출자가 @Transactional 경계 밖(open-in-view=false)에서 불러도 안전하다 — 예: 이미지 업로드/외부 URL 임포트(네트워크 I/O 때문에 DB
   * 트랜잭션을 길게 잡으면 안 되는 경로). isOwnedBy 는 eager 컬럼(userId)만 읽으므로 detached 상태에서도 동작한다.
   */
  public void verifyOwned(Long userId, Long postId) {
    loadOwned(userId, postId);
  }

  private PostEntity loadOwned(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return post;
  }
}
