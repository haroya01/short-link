package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.read.MuteStatus;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MuteUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private MuteRepository muteRepository;

  private MuteUseCase useCase() {
    return new MuteUseCase(userRepository, muteRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void aNewMuteSilencesNoticesByDefaultAndCanEndAfterADuration() {
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(muteRepository.find(9L, 2L)).thenReturn(Optional.empty());
    when(muteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    MuteStatus forever = useCase().mute(9L, "bob", null, null);
    MuteStatus day = useCase().mute(9L, "bob", false, 86_400L);

    assertThat(forever).isEqualTo(new MuteStatus(true, true, null));
    assertThat(day).isEqualTo(new MuteStatus(true, false, NOW.plusSeconds(86_400)));
    ArgumentCaptor<UserMuteEntity> saved = ArgumentCaptor.forClass(UserMuteEntity.class);
    verify(muteRepository, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues().getFirst().getMutedUserId()).isEqualTo(2L);
  }

  @Test
  void mutingAgainChangesTheExistingMute() {
    UserMuteEntity existing = new UserMuteEntity(9L, 2L, true, null);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(muteRepository.find(9L, 2L)).thenReturn(Optional.of(existing));

    assertThat(useCase().mute(9L, "bob", false, 3600L))
        .isEqualTo(new MuteStatus(true, false, NOW.plusSeconds(3600)));
    verify(muteRepository, never()).save(any());
    assertThat(existing.isHideNotifications()).isFalse();
  }

  @Test
  void noOneMutesThemselvesAndADurationIsAMinuteToAYear() {
    when(userRepository.findByUsername("me")).thenReturn(Optional.of(user(9L, "me")));
    assertThatThrownBy(() -> useCase().mute(9L, "me", null, null))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.CANNOT_MUTE_SELF));

    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    for (long duration : new long[] {59, UserMuteEntity.MAX_DURATION_SECONDS + 1}) {
      assertThatThrownBy(() -> useCase().mute(9L, "bob", null, duration))
          .isInstanceOfSatisfying(
              UserException.class,
              e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.MUTE_DURATION_INVALID));
    }
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase().unmute(9L, "ghost")).isInstanceOf(UserException.class);
  }

  @Test
  void statusReadsOnlyALiveMuteAndUnmuteDeletesIt() {
    UserMuteEntity live = new UserMuteEntity(9L, 2L, false, NOW.plusSeconds(60));
    UserMuteEntity over = new UserMuteEntity(9L, 3L, true, NOW);
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user(2L, "bob")));
    when(userRepository.findByUsername("cat")).thenReturn(Optional.of(user(3L, "cat")));
    when(userRepository.findByUsername("dan")).thenReturn(Optional.of(user(4L, "dan")));
    when(muteRepository.find(9L, 2L)).thenReturn(Optional.of(live));
    when(muteRepository.find(9L, 3L)).thenReturn(Optional.of(over));
    when(muteRepository.find(9L, 4L)).thenReturn(Optional.empty());

    assertThat(useCase().status(9L, "bob"))
        .isEqualTo(new MuteStatus(true, false, NOW.plusSeconds(60)));
    assertThat(useCase().status(9L, "cat")).isEqualTo(MuteStatus.NONE);
    assertThat(useCase().status(9L, "dan")).isEqualTo(MuteStatus.NONE);

    useCase().unmute(9L, "bob");
    verify(muteRepository).delete(live);
  }
}
