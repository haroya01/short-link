package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 권한 가드를 @Transactional 경계 밖에서 부르는 호출자(이미지 업로드/외부 URL 임포트 — 네트워크 I/O 때문에 DB 트랜잭션을 길게 잡을 수 없는 경로)를
 * 위한 불변식. open-in-view=false 라 감싸는 트랜잭션이 없으면 findById 가 자기 세션을 열고 바로 닫아 엔티티가 detach 된다.
 *
 * <p>{@link PostOwnership#verifyOwned} 는 lazy tags 를 건드리지 않으므로 세션이 없어도 안전해야 하고, {@link
 * PostOwnership#requireOwned} 는 그 자리에서 tags 를 초기화하므로 세션이 없으면 터진다 — 이미지 서비스가 verifyOwned 를 써야 하는 이유.
 * 이 차이는 세션이 실제로 닫히는 DB 동작에서만 드러나므로 일부러 @Transactional 을 붙이지 않는다. (PostImageService.importFromUrl 의
 * prod 재현 회귀)
 */
@SpringBootTest
@ActiveProfiles("test")
class PostOwnershipVerifyOwnedIntegrationTest {

  @Autowired private PostOwnership postOwnership;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;

  private PostEntity savedPost;
  private Long userId;

  @AfterEach
  void cleanup() {
    if (savedPost != null) {
      postRepository.delete(savedPost);
    }
    if (userId != null) {
      userRepository.deleteById(userId);
    }
  }

  @Test
  void verifyOwnedDoesNotTouchTagsSoItSurvivesWithoutASession() {
    UserEntity author =
        userRepository.save(new UserEntity("verify-owner@x.com", "google", "g-verify-owner"));
    userId = author.getId();
    PostEntity post = new PostEntity(userId, "verify-post", "Verify Post", "ko");
    post.updateTags(List.of("spring", "jpa"));
    savedPost = postRepository.save(post);
    Long postId = savedPost.getId();

    // 트랜잭션 밖(이 메서드엔 @Transactional 이 없다) — findById 의 세션은 이미 닫혀 있다.
    assertThatCode(() -> postOwnership.verifyOwned(userId, postId)).doesNotThrowAnyException();

    // 같은 무세션 조건에서 requireOwned 는 tags 초기화를 시도하다 터진다 — 가드 전용 경로가
    // requireOwned 를 쓰면 안 되는 이유(이번 prod LazyInitializationException 의 정체).
    assertThatThrownBy(() -> postOwnership.requireOwned(userId, postId))
        .isInstanceOf(LazyInitializationException.class);
  }
}
