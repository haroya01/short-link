package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.note.application.write.NoteFilterService.Draft;
import com.example.short_link.note.application.write.NoteFilterService.FilterView;
import com.example.short_link.note.domain.NoteFilterEntity;
import com.example.short_link.note.domain.NoteFilterEntity.Action;
import com.example.short_link.note.domain.NoteFilterEntity.Context;
import com.example.short_link.note.domain.repository.NoteFilterRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoteFilterServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @Mock private NoteFilterRepository filters;

  private NoteFilterService service() {
    return new NoteFilterService(filters, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static void invalid(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_FILTER_INVALID));
  }

  @Test
  void aFilterKeepsItsTrimmedPhraseContextsAndActionAndWarnsByDefault() {
    when(filters.count(7L)).thenReturn(0L);
    when(filters.save(any()))
        .thenAnswer(
            inv -> {
              NoteFilterEntity filter = inv.getArgument(0);
              ReflectionTestUtils.setField(filter, "id", 3L);
              return filter;
            });

    FilterView view =
        service().create(7L, new Draft("  스포일러 ", null, List.of("home", "thread"), null, 3600L));

    assertThat(view)
        .isEqualTo(
            new FilterView(
                3L, "스포일러", false, List.of("home", "thread"), "warn", NOW.plusSeconds(3600)));
    FilterView hide = service().create(7L, new Draft("광고", true, List.of("public"), "hide", null));
    assertThat(hide.action()).isEqualTo("hide");
    assertThat(hide.wholeWord()).isTrue();
    assertThat(hide.expiresAt()).isNull();
  }

  @Test
  void aPhraseContextsActionAndDurationMustBeSensible() {
    for (Draft draft :
        List.of(
            new Draft("  ", null, List.of("home"), null, null),
            new Draft(null, null, List.of("home"), null, null),
            new Draft("가".repeat(101), null, List.of("home"), null, null),
            new Draft("x", null, List.of(), null, null),
            new Draft("x", null, null, null, null),
            new Draft("x", null, List.of("timeline"), null, null),
            new Draft("x", null, List.of("home"), "mute", null),
            new Draft("x", null, List.of("home"), null, 59L),
            new Draft("x", null, List.of("home"), null, 365L * 24 * 3600 + 1))) {
      invalid(() -> service().create(7L, draft));
    }
    verify(filters, never()).save(any());
  }

  @Test
  void anOwnerHasAtMostAHundredFilters() {
    when(filters.count(7L)).thenReturn(100L);

    assertThatThrownBy(
            () -> service().create(7L, new Draft("x", null, List.of("home"), null, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_FILTER_LIMIT));
  }

  @Test
  void onlyTheOwnerChangesOrDeletesAFilterAndTheListIsTheLiveOnes() {
    NoteFilterEntity filter =
        new NoteFilterEntity(7L, "옛 말", false, EnumSet.of(Context.HOME), Action.WARN, null);
    ReflectionTestUtils.setField(filter, "id", 3L);
    when(filters.owned(3L, 7L)).thenReturn(Optional.of(filter));
    when(filters.owned(3L, 8L)).thenReturn(Optional.empty());

    FilterView changed =
        service()
            .update(
                7L, 3L, new Draft("새 말", true, List.of("notifications", "account"), "hide", null));
    assertThat(changed.context()).containsExactly("account", "notifications");
    assertThat(changed.phrase()).isEqualTo("새 말");
    assertThatThrownBy(
            () -> service().update(8L, 3L, new Draft("x", null, List.of("home"), null, null)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_FILTER_NOT_FOUND));

    service().delete(7L, 3L);
    verify(filters).delete(filter);

    when(filters.live(7L, NOW)).thenReturn(List.of(filter));
    assertThat(service().mine(7L)).extracting(FilterView::id).containsExactly(3L);
  }
}
