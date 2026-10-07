package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;
  @Mock private FollowRequestRepository followRequests;
  @Mock private BlockRepository blockRepository;
  @Mock private org.springframework.context.ApplicationEventPublisher events;

  private FollowUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new FollowUseCase(
            userRepository, followRepository, followRequests, blockRepository, events);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void followCreatesEdgeWhenNew() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(false);
    when(followRepository.countByFollowingId(2L)).thenReturn(1L);
    when(followRepository.countByFollowerId(2L)).thenReturn(0L);

    FollowStatus status = useCase.follow(9L, "bob", 42L);

    assertThat(status.following()).isTrue();
    assertThat(status.followerCount()).isEqualTo(1);
    org.mockito.ArgumentCaptor<FollowEntity> saved =
        org.mockito.ArgumentCaptor.forClass(FollowEntity.class);
    verify(followRepository).save(saved.capture());
    assertThat(saved.getValue().getSourcePostId()).isEqualTo(42L);
    org.mockito.ArgumentCaptor<com.example.short_link.common.event.BlogInteractionEvent> evt =
        org.mockito.ArgumentCaptor.forClass(
            com.example.short_link.common.event.BlogInteractionEvent.class);
    verify(events).publishEvent(evt.capture());
    assertThat(evt.getValue().type())
        .isEqualTo(com.example.short_link.common.event.BlogInteractionType.FOLLOW);
    assertThat(evt.getValue().recipientUserId()).isEqualTo(2L);
    assertThat(evt.getValue().actorUserId()).isEqualTo(9L);
  }

  @Test
  void followIsIdempotent() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(true);
    when(followRepository.countByFollowingId(2L)).thenReturn(1L);
    when(followRepository.countByFollowerId(2L)).thenReturn(0L);

    FollowStatus status = useCase.follow(9L, "bob", null);

    assertThat(status.following()).isTrue();
    verify(followRepository, never()).save(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void followingYourselfIsRejected() {
    when(userRepository.findByUsername("me")).thenReturn(Optional.of(user(9L, "me")));

    assertThatThrownBy(() -> useCase.follow(9L, "me", null)).isInstanceOf(UserException.class);
    verify(followRepository, never()).save(any());
  }

  @Test
  void followingUnknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.follow(9L, "ghost", null)).isInstanceOf(UserException.class);
  }

  @Test
  void followRejectedWhenTargetBlockedFollower() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(blockRepository.existsByBlockerIdAndBlockedId(2L, 9L)).thenReturn(true);

    assertThatThrownBy(() -> useCase.follow(9L, "bob", null))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.BLOCKED_TARGET);
    verify(followRepository, never()).save(any());
  }

  @Test
  void unfollowDeletesExistingEdge() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findByFollowerIdAndFollowingId(9L, 2L))
        .thenReturn(Optional.of(new FollowEntity(9L, 2L)));
    when(followRepository.countByFollowingId(2L)).thenReturn(0L);
    when(followRepository.countByFollowerId(2L)).thenReturn(0L);

    FollowStatus status = useCase.unfollow(9L, "bob");

    assertThat(status.following()).isFalse();
    verify(followRepository).delete(any(FollowEntity.class));
  }

  @Test
  void followReturnsHiddenStatusWhenTargetHidesCounts() {
    UserEntity bob = user(2L, "bob");
    bob.updateHideFollowerCount(true);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(false);

    FollowStatus status = useCase.follow(9L, "bob", null);

    assertThat(status.following()).isTrue();
    assertThat(status.hideFollowerCount()).isTrue();
    assertThat(status.followerCount()).isNull();
    assertThat(status.followingCount()).isNull();
  }

  @Test
  void onlyAFollowerCanAskToHearOfEveryNewNote() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    FollowEntity follow = new FollowEntity(9L, 2L);
    when(followRepository.findByFollowerIdAndFollowingId(9L, 2L)).thenReturn(Optional.of(follow));

    assertThat(useCase.setNoteNotifications(9L, "bob", true).notifyNotes()).isTrue();
    assertThat(follow.isNotifyNotes()).isTrue();
    assertThat(useCase.setNoteNotifications(9L, "bob", false).notifyNotes()).isFalse();
    assertThat(follow.isNotifyNotes()).isFalse();

    when(followRepository.findByFollowerIdAndFollowingId(8L, 2L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.setNoteNotifications(8L, "bob", true))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.NOT_FOLLOWING));
  }

  private UserEntity locked(long id, String username) {
    UserEntity u = user(id, username);
    u.updateLocked(true);
    return u;
  }

  @Test
  void followingALockedAccountLeavesARequestInstead() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(locked(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(false);
    when(followRequests.exists(9L, 2L)).thenReturn(false);

    FollowStatus status = useCase.follow(9L, "bob", null);

    assertThat(status.following()).isFalse();
    assertThat(status.requested()).isTrue();
    assertThat(status.locked()).isTrue();
    verify(followRequests).save(any(FollowRequestEntity.class));
    verify(followRepository, never()).save(any());
    verify(events).publishEvent(new FollowRequestedEvent(2L, 9L, null));
  }

  @Test
  void askingALockedAccountAgainLeavesOneRequest() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(locked(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(false);
    when(followRequests.exists(9L, 2L)).thenReturn(true);

    assertThat(useCase.follow(9L, "bob", null).requested()).isTrue();
    verify(followRequests, never()).save(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void aFollowerOfALockedAccountStaysAFollower() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(locked(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(true);

    FollowStatus status = useCase.follow(9L, "bob", null);

    assertThat(status.following()).isTrue();
    assertThat(status.requested()).isFalse();
    verifyNoInteractions(followRequests);
  }

  @Test
  void unfollowingALockedAccountWithdrawsTheRequest() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(locked(2L, "bob")));
    when(followRepository.findByFollowerIdAndFollowingId(9L, 2L)).thenReturn(Optional.empty());
    when(followRequests.delete(9L, 2L)).thenReturn(1);

    FollowStatus status = useCase.unfollow(9L, "bob");

    assertThat(status.requested()).isFalse();
    verify(events).publishEvent(new FollowRequestSettledEvent(2L, 9L, null));
  }

  @Test
  void withdrawingARequestThatIsNotThereSaysNothing() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(locked(2L, "bob")));
    when(followRepository.findByFollowerIdAndFollowingId(9L, 2L)).thenReturn(Optional.empty());
    when(followRequests.delete(9L, 2L)).thenReturn(0);

    useCase.unfollow(9L, "bob");

    verify(events, never()).publishEvent(any());
  }

  @Test
  void unfollowingAnOpenAccountNeverTouchesRequests() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findByFollowerIdAndFollowingId(9L, 2L)).thenReturn(Optional.empty());

    useCase.unfollow(9L, "bob");

    verifyNoInteractions(followRequests);
  }
}
