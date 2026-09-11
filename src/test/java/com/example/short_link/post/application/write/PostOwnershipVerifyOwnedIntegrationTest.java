package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 소유권 검사만 하는 이미지 경로는 트랜잭션 밖에서도 lazy 태그를 건드리지 않는다. */
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

    assertThatCode(() -> postOwnership.requireOwned(userId, postId)).doesNotThrowAnyException();
  }
}
