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

/** 쓰기 응답은 트랜잭션 안에서 완성되며, 소유권 검사 자체는 태그를 로딩하지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostOwnershipTagsInitializationIntegrationTest {

  @Autowired private PostOwnership postOwnership;
  @Autowired private SchedulePostUseCase schedulePost;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @PersistenceContext private EntityManager em;

  @Test
  void writeResponseIncludesTagsAfterDetachWithoutLoadingThemInOwnership() {
    UserEntity author =
        userRepository.save(new UserEntity("tags-owner@x.com", "google", "g-tags-owner"));
    PostEntity post = new PostEntity(author.getId(), "tagged-post", "Tagged Post", "ko");
    post.updateTags(List.of("spring", "jpa"));
    Long postId = postRepository.save(post).getId();

    // 컨트롤러가 새 요청에서 글을 다시 불러오는 상황처럼, 영속성 컨텍스트를 비워 tags 를 lazy 상태로 만든다.
    em.flush();
    em.clear();

    PostEntity loaded = postOwnership.requireOwned(author.getId(), postId);
    assertThat(Hibernate.isInitialized(loaded.getTags())).isFalse();
    var response =
        schedulePost.execute(
            new SchedulePostCommand(
                author.getId(), postId, java.time.Instant.now().plusSeconds(3600)));
    em.clear();
    assertThatCode(() -> assertThat(response.tags()).containsExactly("spring", "jpa"))
        .doesNotThrowAnyException();
  }
}
