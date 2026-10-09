package com.example.short_link.user.application.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

  @Test
  void aMutedConversationIsAskedInTheSameQueryAndNothingToAskIsNoQuery() {
    when(mutes.silences(9L, 2L, null, 40L, NOW)).thenReturn(true);
    when(mutes.silences(9L, null, null, 41L, NOW)).thenReturn(false);
    when(mutes.silences(9L, null, 70L, null, NOW)).thenReturn(true);

    assertThat(adapter().silences(9L, 2L, null, 40L)).isTrue();
    assertThat(adapter().silences(9L, null, null, 41L)).isFalse();
    assertThat(adapter().silences(9L, null, 70L, null)).isTrue();
    assertThat(adapter().silences(9L, null, null, null)).isFalse();
    assertThat(adapter().silences(null, 2L, null, 40L)).isFalse();
    verifyNoInteractions(blocks);
  }

  @Test
  void theBlockRelationReadsBothDirectionsAtOnceAndNothingForNobodyOrOneself() {
    when(blocks.findBetween(9L, 2L)).thenReturn(List.of(new UserBlockEntity(9L, 2L)));
    when(blocks.findBetween(9L, 3L)).thenReturn(List.of(new UserBlockEntity(3L, 9L)));
    when(blocks.findBetween(9L, 4L))
        .thenReturn(List.of(new UserBlockEntity(9L, 4L), new UserBlockEntity(4L, 9L)));
    when(blocks.findBetween(9L, 5L)).thenReturn(List.of());

    assertThat(adapter().between(9L, 2L)).isEqualTo(new BlockRelation(true, false));
    assertThat(adapter().between(9L, 3L)).isEqualTo(new BlockRelation(false, true));
    assertThat(adapter().between(9L, 4L)).isEqualTo(new BlockRelation(true, true));
    assertThat(adapter().between(9L, 5L)).isEqualTo(BlockRelation.NONE);
    assertThat(adapter().between(null, 2L)).isEqualTo(BlockRelation.NONE);
    assertThat(adapter().between(9L, 9L)).isEqualTo(BlockRelation.NONE);
    verifyNoInteractions(mutes);
  }
}
