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

/**
 * Exercises the like flow against a real DB — the {@code INSERT IGNORE}, the atomic counter {@code
 * UPDATE}, and the {@code likeCount > 0} clamp only exist at SQL level, so a mock unit test can't
 * prove the denormalized counter actually tracks the like rows or stays non-negative.
 */
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
    return postRepository.save(new PostEntity(author.getId(), "slug", "Title", "ko")).getId();
  }

  private long user(String suffix) {
    return userRepository.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix)).getId();
  }

  /**
   * Denormalized counter read fresh from the DB — clears the stale entity left by the bulk UPDATE.
   */
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
