package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.domain.FollowEntity;
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
class FollowUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;

  private FollowUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new FollowUseCase(userRepository, followRepository);
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

    FollowStatus status = useCase.follow(9L, "bob");

    assertThat(status.following()).isTrue();
    assertThat(status.followerCount()).isEqualTo(1);
    verify(followRepository).save(any(FollowEntity.class));
  }

  @Test
  void followIsIdempotent() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(true);
    when(followRepository.countByFollowingId(2L)).thenReturn(1L);
    when(followRepository.countByFollowerId(2L)).thenReturn(0L);

    FollowStatus status = useCase.follow(9L, "bob");

    assertThat(status.following()).isTrue();
    verify(followRepository, never()).save(any());
  }

  @Test
  void followingYourselfIsRejected() {
    when(userRepository.findByUsername("me")).thenReturn(Optional.of(user(9L, "me")));

    assertThatThrownBy(() -> useCase.follow(9L, "me")).isInstanceOf(UserException.class);
    verify(followRepository, never()).save(any());
  }

  @Test
  void followingUnknownUserThrows() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.follow(9L, "ghost")).isInstanceOf(UserException.class);
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
}
