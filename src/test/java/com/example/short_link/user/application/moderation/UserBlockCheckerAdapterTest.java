package com.example.short_link.user.application.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.common.user.BlockRelation;
import com.example.short_link.common.user.SilenceCheck;
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
    SilenceCheck member = SilenceCheck.of(9L, Actor.member(2L));
    when(mutes.silences(member, NOW)).thenReturn(true);

    assertThat(adapter().silences(member)).isTrue();
    assertThat(adapter().silences(SilenceCheck.of(null, Actor.member(2L)))).isFalse();
    assertThat(adapter().silences(SilenceCheck.of(9L, Actor.NONE))).isFalse();
    verifyNoInteractions(blocks);
  }

  @Test
  void aMutedConversationIsAskedInTheSameQueryAndNothingToAskIsNoQuery() {
    SilenceCheck memberInThread = SilenceCheck.of(9L, Actor.member(2L)).inConversation(40L);
    SilenceCheck nobodyInThread = SilenceCheck.of(9L, Actor.NONE).inConversation(41L);
    SilenceCheck elsewhere = SilenceCheck.of(9L, Actor.remote(70L));
    when(mutes.silences(memberInThread, NOW)).thenReturn(true);
    when(mutes.silences(nobodyInThread, NOW)).thenReturn(false);
    when(mutes.silences(elsewhere, NOW)).thenReturn(true);

    assertThat(adapter().silences(memberInThread)).isTrue();
    assertThat(adapter().silences(nobodyInThread)).isFalse();
    assertThat(adapter().silences(elsewhere)).isTrue();
    assertThat(adapter().silences(SilenceCheck.of(9L, Actor.NONE))).isFalse();
    assertThat(adapter().silences(SilenceCheck.of(null, Actor.member(2L)).inConversation(40L)))
        .isFalse();
    verifyNoInteractions(blocks);
  }

  @Test
  void theBlockRelationReadsBothDirectionsAtOnceAndNothingForNobodyOrOneself() {
    when(blocks.findBetween(9L, 2L))
        .thenReturn(List.of(new UserBlockEntity(BlockEdge.blocker(9L).blocked(2L))));
    when(blocks.findBetween(9L, 3L))
        .thenReturn(List.of(new UserBlockEntity(BlockEdge.blocker(3L).blocked(9L))));
    when(blocks.findBetween(9L, 4L))
        .thenReturn(
            List.of(
                new UserBlockEntity(BlockEdge.blocker(9L).blocked(4L)),
                new UserBlockEntity(BlockEdge.blocker(4L).blocked(9L))));
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
