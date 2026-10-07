package com.example.short_link.user.application.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBlockCheckerAdapterTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @Mock private BlockRepository blocks;
  @Mock private MuteRepository mutes;

  private UserBlockCheckerAdapter adapter() {
    return new UserBlockCheckerAdapter(blocks, mutes, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void silencingAsksTheOneCombinedQueryAndNoOneIsSilencedByNobody() {
    when(mutes.silences(9L, 2L, NOW)).thenReturn(true);

    assertThat(adapter().silences(9L, 2L)).isTrue();
    assertThat(adapter().silences(null, 2L)).isFalse();
    assertThat(adapter().silences(9L, null)).isFalse();
    verifyNoInteractions(blocks);
  }
}
