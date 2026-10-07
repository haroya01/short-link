package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.user.application.read.FollowRequestView;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.PendingFollowRequest;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowRequestUseCaseTest {

  @Mock private UserRepository userRepository;
  @Mock private FollowRepository followRepository;
  @Mock private FollowRequestRepository followRequests;
  @Mock private ApplicationEventPublisher events;

  private FollowRequestUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new FollowRequestUseCase(userRepository, followRepository, followRequests, events);
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listsWaitingRequestsAPageAtATime() {
    Instant at = Instant.parse("2026-10-07T00:00:00Z");
    when(followRequests.pending(2L, 40, FollowRequestUseCase.PAGE_SIZE))
        .thenReturn(List.of(new PendingFollowRequest(9L, "alice", "Alice", null, at)));

    List<FollowRequestView> views = useCase.pending(2L, 1);

    assertThat(views).singleElement().extracting(FollowRequestView::username).isEqualTo("alice");
    assertThat(views.getFirst().requestedAt()).isEqualTo(at);
  }

  @Test
  void aNegativePageIsTheFirstPage() {
    useCase.pending(2L, -3);

    verify(followRequests).pending(2L, 0, FollowRequestUseCase.PAGE_SIZE);
  }

  @Test
  void approvingARequestMakesItAFollow() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice")));
    when(followRequests.delete(9L, 2L)).thenReturn(1);
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(false);

    useCase.authorize(2L, "alice");

    ArgumentCaptor<FollowEntity> saved = ArgumentCaptor.forClass(FollowEntity.class);
    verify(followRepository).save(saved.capture());
    assertThat(saved.getValue().getFollowerId()).isEqualTo(9L);
    assertThat(saved.getValue().getFollowingId()).isEqualTo(2L);
    verify(events).publishEvent(new FollowRequestSettledEvent(2L, 9L, null));
  }

  @Test
  void approvingSomeoneAlreadyFollowingAddsNoSecondFollow() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice")));
    when(followRequests.delete(9L, 2L)).thenReturn(1);
    when(followRepository.existsByFollowerIdAndFollowingId(9L, 2L)).thenReturn(true);

    useCase.authorize(2L, "alice");

    verify(followRepository, never()).save(any());
  }

  @Test
  void turningARequestDownOnlyRemovesIt() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice")));
    when(followRequests.delete(9L, 2L)).thenReturn(1);

    useCase.reject(2L, "alice");

    verifyNoInteractions(followRepository);
    verify(events).publishEvent(new FollowRequestSettledEvent(2L, 9L, null));
  }

  @Test
  void aRequestThatIsNotThereIsNotFound() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user(9L, "alice")));
    when(followRequests.delete(9L, 2L)).thenReturn(0);

    assertThatThrownBy(() -> useCase.authorize(2L, "alice"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.FOLLOW_REQUEST_NOT_FOUND));
    verifyNoInteractions(followRepository, events);
  }

  @Test
  void anUnknownRequesterIsNotFound() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.reject(2L, "ghost"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    verifyNoInteractions(followRequests);
  }

  @Test
  void unlockingLetsEveryoneWaitingIn() {
    useCase.onUnlocked(new AccountUnlockedEvent(2L));

    verify(followRequests).approveAll(2L);
  }
}
