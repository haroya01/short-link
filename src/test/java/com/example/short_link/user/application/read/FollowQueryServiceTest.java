package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
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

  private FollowQueryService service;

  @BeforeEach
  void setUp() {
    service = new FollowQueryService(userRepository, followRepository);
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
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(true);
    when(followRepository.countByFollowingId(2L)).thenReturn(5L);
    when(followRepository.countByFollowerId(2L)).thenReturn(3L);

    FollowStatus status = service.status(9L, "bob");

    assertThat(status.following()).isTrue();
    assertThat(status.followerCount()).isEqualTo(5);
    assertThat(status.followingCount()).isEqualTo(3);
  }

  @Test
  void anonymousViewerIsNeverFollowingButStillSeesCount() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.countByFollowingId(2L)).thenReturn(5L);
    when(followRepository.countByFollowerId(2L)).thenReturn(0L);

    FollowStatus status = service.status(null, "bob");

    assertThat(status.following()).isFalse();
    assertThat(status.followerCount()).isEqualTo(5);
  }

  @Test
  void unknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.status(9L, "ghost")).isInstanceOf(UserException.class);
  }
}
