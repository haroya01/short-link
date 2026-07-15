package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowListQueryServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;

  private FollowListQueryService service;

  @BeforeEach
  void setUp() {
    service = new FollowListQueryService(userRepository, followRepository);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    if (username != null) {
      u.claimUsername(username);
    }
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private UserEntity deleted(long id, String username) {
    UserEntity u = user(id, username);
    ReflectionTestUtils.setField(u, "deletedAt", Instant.now());
    return u;
  }

  @Test
  void followersHydratesRowsInOrderWithCountsAndViewerState() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findFollowerIds(2L, 0, 20)).thenReturn(List.of(10L, 11L));
    when(followRepository.countByFollowingId(2L)).thenReturn(50L); // (0+1)*20 < 50 -> hasNext
    when(userRepository.findAllByIdIn(List.of(10L, 11L)))
        .thenReturn(List.of(user(10L, "alice"), user(11L, "carol")));
    when(followRepository.countFollowersByIdIn(anyCollection()))
        .thenReturn(Map.of(10L, 7L, 11L, 3L));
    when(followRepository.findFollowedAmong(eq(9L), anyCollection())).thenReturn(List.of(10L));

    FollowListView view = service.followers(9L, "bob", 0, 20);

    assertThat(view.hasNext()).isTrue();
    assertThat(view.items()).extracting(FollowUserView::id).containsExactly(10L, 11L);
    FollowUserView alice = view.items().get(0);
    assertThat(alice.username()).isEqualTo("alice");
    assertThat(alice.followerCount()).isEqualTo(7);
    assertThat(alice.followedByMe()).isTrue();
    assertThat(view.items().get(1).followedByMe()).isFalse();
  }

  @Test
  void anonymousViewerNeverFollowsAnyoneAndSkipsTheEdgeQuery() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findFollowerIds(2L, 0, 20)).thenReturn(List.of(10L));
    when(followRepository.countByFollowingId(2L)).thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(user(10L, "alice")));
    when(followRepository.countFollowersByIdIn(anyCollection())).thenReturn(Map.of(10L, 7L));

    FollowListView view = service.followers(null, "bob", 0, 20);

    assertThat(view.items().get(0).followedByMe()).isFalse();
    verify(followRepository, never()).findFollowedAmong(eq(null), anyCollection());
  }

  @Test
  void followingSkipsDeletedAndUnnamedUsersAndReportsHasNext() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findFollowingIds(2L, 0, 20)).thenReturn(List.of(10L, 11L, 12L));
    when(followRepository.countByFollowerId(2L)).thenReturn(25L); // (0+1)*20 < 25 -> hasNext
    when(userRepository.findAllByIdIn(List.of(10L, 11L, 12L)))
        .thenReturn(List.of(user(10L, "alice"), deleted(11L, "ghost"), user(12L, null)));
    when(followRepository.countFollowersByIdIn(anyCollection())).thenReturn(Map.of(10L, 7L));
    when(followRepository.findFollowedAmong(eq(9L), anyCollection())).thenReturn(List.of());

    FollowListView view = service.following(9L, "bob", 0, 20);

    assertThat(view.hasNext()).isTrue();
    assertThat(view.items()).extracting(FollowUserView::id).containsExactly(10L);
    assertThat(view.items().get(0).followerCount()).isEqualTo(7);
    assertThat(view.items().get(0).followedByMe()).isFalse();
  }

  @Test
  void followingReportsNoNextWhenPageCoversEveryone() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findFollowingIds(2L, 0, 20)).thenReturn(List.of(10L));
    when(followRepository.countByFollowerId(2L)).thenReturn(1L); // (0+1)*20 < 1 -> no next
    when(userRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(user(10L, "alice")));
    when(followRepository.countFollowersByIdIn(anyCollection())).thenReturn(Map.of(10L, 7L));
    when(followRepository.findFollowedAmong(eq(9L), anyCollection())).thenReturn(List.of(10L));

    FollowListView view = service.following(9L, "bob", 0, 20);

    assertThat(view.hasNext()).isFalse();
    assertThat(view.items().get(0).followedByMe()).isTrue();
  }

  @Test
  void emptyPageReturnsNoRows() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.findFollowerIds(2L, 1, 20)).thenReturn(List.of());
    when(followRepository.countByFollowingId(2L)).thenReturn(20L);

    FollowListView view = service.followers(9L, "bob", 1, 20);

    assertThat(view.items()).isEmpty();
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void unknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.followers(9L, "ghost", 0, 20))
        .isInstanceOf(UserException.class);
  }

  private UserEntity hidingUser(long id, String username) {
    UserEntity u = user(id, username);
    u.updateHideFollowerCount(true);
    return u;
  }

  @Test
  void hiddenCountsLockBothListsForOtherViewers() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(hidingUser(2L, "bob")));

    assertThatThrownBy(() -> service.followers(9L, "bob", 0, 20))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.FOLLOW_LIST_HIDDEN);
    assertThatThrownBy(() -> service.following(9L, "bob", 0, 20))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.FOLLOW_LIST_HIDDEN);
    verify(followRepository, never()).findFollowerIds(2L, 0, 20);
  }

  @Test
  void hiddenCountsLockListsForAnonymousViewers() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(hidingUser(2L, "bob")));

    assertThatThrownBy(() -> service.followers(null, "bob", 0, 20))
        .isInstanceOf(UserException.class)
        .extracting(e -> ((UserException) e).errorCode())
        .isEqualTo(UserErrorCode.FOLLOW_LIST_HIDDEN);
  }

  @Test
  void ownerStillSeesTheirOwnListsWhenHidden() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(hidingUser(2L, "bob")));
    when(followRepository.findFollowerIds(2L, 0, 20)).thenReturn(List.of(10L));
    when(followRepository.countByFollowingId(2L)).thenReturn(1L);
    when(userRepository.findAllByIdIn(List.of(10L))).thenReturn(List.of(user(10L, "alice")));
    when(followRepository.countFollowersByIdIn(anyCollection())).thenReturn(Map.of(10L, 7L));
    when(followRepository.findFollowedAmong(eq(2L), anyCollection())).thenReturn(List.of());

    FollowListView view = service.followers(2L, "bob", 0, 20);

    assertThat(view.items()).extracting(FollowUserView::id).containsExactly(10L);
  }
}
