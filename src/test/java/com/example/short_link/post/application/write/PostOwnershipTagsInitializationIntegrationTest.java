package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * open-in-view=false(#574) 이후, 쓰기 컨트롤러는 트랜잭션이 끝난 detached 엔티티를 PostView.from() 으로 매핑하면서 lazy 인
 * tags(@ElementCollection)를 읽는다 — 그대로면 LazyInitializationException(발행 시 재현). 모든 쓰기 유스케이스가 거치는
 * {@link PostOwnership#requireOwned}가 세션 안에서 tags 를 미리 초기화하므로 detach 이후에도 안전해야 한다. 이 불변식은 세션이 닫히는
 * 실제 DB 동작에서만 드러나므로 mock 으로는 못 박는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostOwnershipTagsInitializationIntegrationTest {

  @Autowired private PostOwnership postOwnership;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @PersistenceContext private EntityManager em;

  @Test
  void requireOwnedInitializesTagsSoTheySurviveDetach() {
    UserEntity author =
        userRepository.save(new UserEntity("tags-owner@x.com", "google", "g-tags-owner"));
    PostEntity post = new PostEntity(author.getId(), "tagged-post", "Tagged Post", "ko");
    post.updateTags(List.of("spring", "jpa"));
    Long postId = postRepository.save(post).getId();

    // 컨트롤러가 새 요청에서 글을 다시 불러오는 상황처럼, 영속성 컨텍스트를 비워 tags 를 lazy 상태로 만든다.
    em.flush();
    em.clear();

    PostEntity loaded = postOwnership.requireOwned(author.getId(), postId);
    assertThat(Hibernate.isInitialized(loaded.getTags())).isTrue();

    // detach = 트랜잭션 종료 후 컨트롤러 매핑과 동일한 조건. 여기서 tags 를 읽어도 예외가 없어야 한다.
    em.detach(loaded);
    assertThatCode(() -> assertThat(loaded.getTags()).containsExactly("spring", "jpa"))
        .doesNotThrowAnyException();
  }
}
