package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockEndsFollowsIntegrationTest {

  @Autowired private BlockUseCase blocks;
  @Autowired private FollowRepository follows;
  @Autowired private FollowRequestRepository followRequests;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  private long alice;
  private long bob;
  private long carol;

  private long user(String handle) {
    UserEntity u = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }

  private void follow(long follower, long following) {
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        follower,
        following);
  }

  private void request(long follower, long following) {
    jdbc.update(
        "INSERT INTO follow_request (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        follower,
        following);
  }

  @BeforeEach
  void setUp() {
    alice = user("bef-alice");
    bob = user("bef-bob");
    carol = user("bef-carol");
  }

  @Test
  void blockingEndsTheFollowAndRequestsBetweenTheTwoOnly() {
    follow(alice, bob);
    follow(bob, alice);
    follow(carol, alice);
    request(alice, carol);
    request(bob, carol);

    blocks.block(carol, "bef-bob");
    blocks.block(alice, "bef-bob");

    assertThat(follows.exists(FollowEdge.follower(alice).following(bob))).isFalse();
    assertThat(follows.exists(FollowEdge.follower(bob).following(alice))).isFalse();
    assertThat(follows.exists(FollowEdge.follower(carol).following(alice))).isTrue();
    assertThat(followRequests.exists(FollowEdge.follower(bob).following(carol))).isFalse();
    assertThat(followRequests.exists(FollowEdge.follower(alice).following(carol))).isTrue();
  }

  @Test
  void unlockingAdmitsNoRequesterBlockedEitherWayOrGone() {
    request(alice, carol);
    request(bob, carol);
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        carol,
        bob);
    long gone = user("bef-gone");
    request(gone, carol);
    jdbc.update("UPDATE users SET deleted_at = NOW(6) WHERE id = ?", gone);

    followRequests.approveAll(carol);

    assertThat(follows.exists(FollowEdge.follower(alice).following(carol))).isTrue();
    assertThat(follows.exists(FollowEdge.follower(bob).following(carol))).isFalse();
    assertThat(follows.exists(FollowEdge.follower(gone).following(carol))).isFalse();
    assertThat(followRequests.exists(FollowEdge.follower(bob).following(carol))).isFalse();
  }
}
