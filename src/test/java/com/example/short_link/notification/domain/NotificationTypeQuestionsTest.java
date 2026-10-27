package com.example.short_link.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class NotificationTypeQuestionsTest {

  private static EnumSet<NotificationType> where(Predicate<NotificationType> q) {
    EnumSet<NotificationType> types = EnumSet.noneOf(NotificationType.class);
    Arrays.stream(NotificationType.values()).filter(q).forEach(types::add);
    return types;
  }

  @Test
  void keywordFiltersReadTheCauseOfARepliedOrQuotedNoteAndTheNoteOfTheOthers() {
    assertThat(where(t -> t.keywordText() == NotificationType.KeywordText.SOURCE_EXCERPT))
        .containsExactlyInAnyOrder(NotificationType.NOTE_REPLY, NotificationType.NOTE_QUOTE);
    assertThat(where(t -> t.keywordText() == NotificationType.KeywordText.EXCERPT))
        .containsExactlyInAnyOrder(
            NotificationType.NOTE_MENTION,
            NotificationType.POST_QUOTE,
            NotificationType.NOTE_POST,
            NotificationType.NOTE_EDIT,
            NotificationType.NOTE_POLL);
  }

  @Test
  void onlyWhatTheActorPublishedOpensTheirWork() {
    assertThat(where(NotificationType::opensTheActorsWork))
        .containsExactlyInAnyOrder(
            NotificationType.NEW_POST,
            NotificationType.NOTE_POLL,
            NotificationType.NOTE_POST,
            NotificationType.NOTE_EDIT,
            NotificationType.POST_QUOTE);
  }

  @Test
  void onlyANoteMentionMayBePrivate() {
    assertThat(where(NotificationType::mayBePrivateMention))
        .containsExactly(NotificationType.NOTE_MENTION);
  }
}
