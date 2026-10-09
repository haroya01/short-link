package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FollowCountsMatchListsIntegrationTest {

  @Autowired private FollowQueryService followQuery;
  @Autowired private FollowListQueryService followLists;
  @Autowired private FollowUseCase followUseCase;
  @Autowired private FollowRepository followRepository;
  @Autowired private UserRepository userRepository;

  private long target;
  private long ann;
  private long ben;
  private long leaving;

  @BeforeEach
  void aTargetFollowedByTwoMembersAndOneAccountWaitingOutItsDeletion() {
    target = user("fcm-target");
    ann = user("fcm-ann");
    ben = user("fcm-ben");
    leaving = user("fcm-leaving");
    long unnamed =
        userRepository.save(new UserEntity("fcm-unnamed@x.com", "google", "g-unnamed")).getId();
    for (long follower : new long[] {ann, ben, leaving, unnamed}) {
      followRepository.save(new FollowEntity(follower, target));
    }
    followRepository.save(new FollowEntity(target, ann));
    followRepository.save(new FollowEntity(target, leaving));
    followRepository.save(new FollowEntity(leaving, ann));
    userRepository.findById(leaving).orElseThrow().softDelete();
  }

  @Test
  void theFollowerAndFollowingCountsAreTheListsLengths() {
    FollowStatus status = followQuery.status(null, "fcm-target");
    FollowListView followers = followLists.followers(null, "fcm-target", 0, 2);
    FollowListView following = followLists.following(null, "fcm-target", 0, 1);

    assertThat(status.followerCount()).isEqualTo(2);
    assertThat(followers.items())
        .extracting(FollowUserView::username)
        .containsExactlyInAnyOrder("fcm-ann", "fcm-ben");
    assertThat(followers.hasNext()).isFalse();
    assertThat(status.followingCount()).isEqualTo(1);
    assertThat(following.items()).extracting(FollowUserView::username).containsExactly("fcm-ann");
    assertThat(following.hasNext()).isFalse();
  }

  @Test
  void eachListedAccountCarriesItsOwnLiveFollowerCount() {
    FollowListView following = followLists.following(null, "fcm-target", 0, 20);

    assertThat(following.items())
        .singleElement()
        .extracting(FollowUserView::followerCount)
        .isEqualTo(1L);
  }

  @Test
  void anAccountWaitingOutItsDeletionCannotBeFollowed() {
    assertThatThrownBy(() -> followUseCase.follow(ann, "fcm-leaving", null))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    assertThat(followRepository.existsByFollowerIdAndFollowingId(ann, leaving)).isFalse();
  }

  private long user(String handle) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }
}
