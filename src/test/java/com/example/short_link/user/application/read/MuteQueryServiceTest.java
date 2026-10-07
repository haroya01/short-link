package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MuteQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private MuteRepository muteRepository;

  private MuteQueryService service() {
    return new MuteQueryService(userRepository, muteRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    if (username != null) {
      u.claimUsername(username);
    }
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void myMutesListLiveMutesNewestFirstWithoutPeopleWhoLeft() {
    when(muteRepository.active(9L, NOW))
        .thenReturn(
            List.of(
                new UserMuteEntity(9L, 2L, true, null),
                new UserMuteEntity(9L, 3L, false, NOW.plusSeconds(60)),
                new UserMuteEntity(9L, 4L, true, null)));
    when(userRepository.findAllByIdIn(List.of(2L, 3L, 4L)))
        .thenReturn(List.of(user(3L, "cat"), user(2L, "bob"), user(4L, null)));

    assertThat(service().myMutes(9L))
        .containsExactly(
            new MutedUserView(2L, "bob", null, true, null),
            new MutedUserView(3L, "cat", null, false, NOW.plusSeconds(60)));
  }

  @Test
  void noMutesReadNoPeople() {
    when(muteRepository.active(9L, NOW)).thenReturn(List.of());

    assertThat(service().myMutes(9L)).isEmpty();
    verifyNoInteractions(userRepository);
  }
}
