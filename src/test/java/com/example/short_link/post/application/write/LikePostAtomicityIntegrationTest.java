package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 실제 DB에서 멱등 INSERT와 카운터 증감이 좋아요 행 수와 일치하는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LikePostAtomicityIntegrationTest {

  @Autowired private LikePostUseCase likePost;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @PersistenceContext private EntityManager em;

  private long persistPost() {
    UserEntity author = userRepository.save(new UserEntity("author@x.com", "google", "g-author"));
    PostEntity post = new PostEntity(author.getId(), "slug", "Title", "ko");
    post.publish();
    return postRepository.save(post).getId();
  }

  private long user(String suffix) {
    return userRepository.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix)).getId();
  }

  /** Bulk UPDATE 이전의 엔티티를 비우고 DB의 카운터를 다시 읽는다. */
  private long denormalizedLikeCount(long postId) {
    em.flush();
    em.clear();
    return postRepository.findById(postId).orElseThrow().getLikeCount();
  }

  @Test
  void likeIsIdempotentAndCounterTracksRowsExactlyOnce() {
    long postId = persistPost();
    long liker = user("liker");

    assertThat(likePost.like(liker, postId).likeCount()).isEqualTo(1);
    // Second like by the same user — INSERT IGNORE is a no-op, counter must not double-count.
    assertThat(likePost.like(liker, postId).likeCount()).isEqualTo(1);
    assertThat(denormalizedLikeCount(postId)).isEqualTo(1);
  }

  @Test
  void distinctUsersEachAddToTheCount() {
    long postId = persistPost();

    assertThat(likePost.like(user("u1"), postId).likeCount()).isEqualTo(1);
    assertThat(likePost.like(user("u2"), postId).likeCount()).isEqualTo(2);
    assertThat(denormalizedLikeCount(postId)).isEqualTo(2);
  }

  @Test
  void unlikeDropsCounterAndNeverGoesNegative() {
    long postId = persistPost();
    long liker = user("liker");
    likePost.like(liker, postId);

    assertThat(likePost.unlike(liker, postId).likeCount()).isZero();
    assertThat(denormalizedLikeCount(postId)).isZero();

    // Unliking again with no like present must not decrement below zero.
    assertThat(likePost.unlike(liker, postId).likeCount()).isZero();
    assertThat(denormalizedLikeCount(postId)).isZero();
  }
}
