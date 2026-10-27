package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowListReader;
import com.example.short_link.user.domain.repository.FollowListReader.Counts;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;
  @Mock private FollowRequestRepository followRequests;
  @Mock private UserBlockChecker userBlocks;
  @Mock private FollowListReader followLists;

  private FollowQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new FollowQueryService(
            userRepository, followRepository, followRequests, followLists, userBlocks);
    lenient().when(followLists.counts(any())).thenReturn(new Counts(0, 0, 0, 0));
    lenient().when(userBlocks.between(any(), any())).thenReturn(BlockRelation.NONE);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void statusReflectsFollowingForAuthenticatedViewer() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    FollowEntity follow = new FollowEntity(FollowEdge.follower(9L).following(2L));
    follow.notifyOfNotes(true);
    when(followRepository.find(FollowEdge.follower(9L).following(2L)))
        .thenReturn(Optional.of(follow));
    when(followLists.counts(2L)).thenReturn(new Counts(5, 3, 4, 2));

    FollowStatus status = service.status(9L, "bob");

    assertThat(status.following()).isTrue();
    assertThat(status.followerCount()).isEqualTo(5);
    assertThat(status.followingCount()).isEqualTo(3);
    assertThat(status.remoteFollowerCount()).isEqualTo(4);
    assertThat(status.remoteFollowingCount()).isEqualTo(2);
    assertThat(status.notifyNotes()).isTrue();
  }

  @Test
  void anonymousViewerIsNeverFollowingButStillSeesCount() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followLists.counts(2L)).thenReturn(new Counts(5, 0, 0, 0));

    FollowStatus status = service.status(null, "bob");

    assertThat(status.following()).isFalse();
    assertThat(status.followerCount()).isEqualTo(5);
  }

  @Test
  void statusCarriesTheBlocksBetweenViewerAndTarget() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(userBlocks.between(9L, 2L)).thenReturn(new BlockRelation(true, false));
    when(userBlocks.between(8L, 2L)).thenReturn(new BlockRelation(false, true));

    FollowStatus blockedByMe = service.status(9L, "bob");
    FollowStatus blockingMe = service.status(8L, "bob");
    FollowStatus anonymous = service.status(null, "bob");

    assertThat(blockedByMe.blockedByViewer()).isTrue();
    assertThat(blockedByMe.blocksViewer()).isFalse();
    assertThat(blockingMe.blockedByViewer()).isFalse();
    assertThat(blockingMe.blocksViewer()).isTrue();
    assertThat(anonymous.blockedByViewer()).isFalse();
    assertThat(anonymous.blocksViewer()).isFalse();
  }

  @Test
  void unknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.status(9L, "ghost")).isInstanceOf(UserException.class);
  }

  @Test
  void hiddenCountOmitsBothTotalsButKeepsFollowingFlag() {
    UserEntity bob = user(2L, "bob");
    bob.updateHideFollowerCount(true);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
    when(followRepository.find(FollowEdge.follower(9L).following(2L)))
        .thenReturn(Optional.of(new FollowEntity(FollowEdge.follower(9L).following(2L))));

    FollowStatus status = service.status(9L, "bob");

    assertThat(status.hideFollowerCount()).isTrue();
    assertThat(status.following()).isTrue();
    assertThat(status.followerCount()).isNull();
    assertThat(status.followingCount()).isNull();
  }

  @Test
  void aViewerWaitingOnALockedAccountSeesTheirRequest() {
    UserEntity bob = user(2L, "bob");
    bob.updateLocked(true);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
    when(followRepository.find(FollowEdge.follower(9L).following(2L))).thenReturn(Optional.empty());
    when(followRequests.exists(FollowEdge.follower(9L).following(2L))).thenReturn(true);

    FollowStatus status = service.status(9L, "bob");

    assertThat(status.following()).isFalse();
    assertThat(status.requested()).isTrue();
    assertThat(status.locked()).isTrue();
  }

  @Test
  void anOpenAccountNeverLooksForRequests() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.find(FollowEdge.follower(9L).following(2L))).thenReturn(Optional.empty());

    FollowStatus status = service.status(9L, "bob");

    assertThat(status.requested()).isFalse();
    assertThat(status.locked()).isFalse();
    org.mockito.Mockito.verifyNoInteractions(followRequests);
  }
}
