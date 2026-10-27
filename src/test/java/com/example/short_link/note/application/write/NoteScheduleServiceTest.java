package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteScheduleEntity;
import com.example.short_link.note.domain.repository.NoteScheduleRepository;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NoteScheduleServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final NoteDraft DRAFT =
      new NoteDraft(
          "내일 아침에 올릴 노트",
          List.of(new NoteDraft.Image("note-images/7/a.png", "alt")),
          null,
          null,
          null,
          "스포일러",
          false,
          "UNLISTED",
          null);

  private static final List<NoteImages.StoredImage> STORED =
      List.of(
          new NoteImages.StoredImage(
              "note-images/7/a.png", "https://cdn/note-images/7/a.png", "image/png", "alt"));

  @Mock private NoteScheduleRepository schedules;
  @Mock private NoteComposer composer;
  @Mock private NoteImages images;

  private NoteScheduleService service() {
    return new NoteScheduleService(
        schedules,
        composer,
        images,
        JSON,
        FakeTransactions.template(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static NoteScheduleEntity row(long id, Instant at) {
    NoteScheduleEntity row = new NoteScheduleEntity(7L, at, JSON.writeValueAsString(DRAFT));
    ReflectionTestUtils.setField(row, "id", id);
    return row;
  }

  @Test
  void aDraftIsCheckedAsIfPostedNowThenKeptAsWrittenForLater() {
    Instant at = NOW.plus(Duration.ofHours(20));
    when(schedules.load(any(), any(), any())).thenReturn(new NoteScheduleRepository.Load(0, 0));
    when(schedules.save(any())).thenAnswer(inv -> inv.getArgument(0));

    when(images.verifyAll(7L, DRAFT.images())).thenReturn(STORED);

    NoteScheduleService.View view = service().schedule(7L, DRAFT, at);

    verify(composer).validate(7L, DRAFT, STORED);
    ArgumentCaptor<NoteScheduleEntity> saved = ArgumentCaptor.forClass(NoteScheduleEntity.class);
    verify(schedules).save(saved.capture());
    assertThat(JSON.readValue(saved.getValue().getDraft(), NoteDraft.class)).isEqualTo(DRAFT);
    assertThat(view.scheduledAt()).isEqualTo(at);
    assertThat(view.body()).isEqualTo("내일 아침에 올릴 노트");
    assertThat(view.contentWarning()).isEqualTo("스포일러");
    assertThat(view.visibility()).isEqualTo("UNLISTED");
    assertThat(view.imageCount()).isEqualTo(1);
    assertThat(view.poll()).isFalse();
    assertThat(view.failure()).isNull();
  }

  @Test
  void aNoteIsScheduledAtLeastFiveMinutesAhead() {
    assertThatThrownBy(() -> service().schedule(7L, DRAFT, NOW.plus(Duration.ofMinutes(4))))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_SCHEDULE_TOO_SOON));
    assertThatThrownBy(() -> service().schedule(7L, DRAFT, null)).isInstanceOf(NoteException.class);
    verify(composer, never()).validate(any(), any(), any());
    verifyNoInteractions(images);
  }

  @Test
  void atMost300WaitAndAtMost25FallOnOneDay() {
    Instant at = NOW.plus(Duration.ofDays(2));
    when(schedules.load(
            7L, Instant.parse("2026-10-09T00:00:00Z"), Instant.parse("2026-10-10T00:00:00Z")))
        .thenReturn(
            new NoteScheduleRepository.Load(300, 0), new NoteScheduleRepository.Load(10, 25));

    assertThatThrownBy(() -> service().schedule(7L, DRAFT, at))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_SCHEDULE_LIMIT));
    assertThatThrownBy(() -> service().schedule(7L, DRAFT, at))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_SCHEDULE_LIMIT));
    verify(schedules, never()).save(any());
  }

  @Test
  void movingANoteClearsItsFailureAndOnlyTheOwnerMovesOrCancelsIt() {
    NoteScheduleEntity row = row(3L, NOW.minusSeconds(60));
    row.fail("NOTE_NOT_FOUND");
    when(schedules.owned(3L, 7L)).thenReturn(Optional.of(row));
    when(schedules.owned(3L, 8L)).thenReturn(Optional.empty());
    when(schedules.save(any())).thenAnswer(inv -> inv.getArgument(0));
    Instant later = NOW.plus(Duration.ofHours(1));

    NoteScheduleService.View moved = service().reschedule(7L, 3L, later);

    assertThat(moved.scheduledAt()).isEqualTo(later);
    assertThat(moved.failure()).isNull();
    assertThatThrownBy(() -> service().cancel(8L, 3L))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_SCHEDULE_NOT_FOUND));
    service().cancel(7L, 3L);
    verify(schedules).delete(row);
  }

  @Test
  void aDueNoteIsPostedAndForgottenOneThatCannotBeKeepsItsReason() {
    NoteScheduleEntity ok = row(1L, NOW.minusSeconds(30));
    NoteScheduleEntity orphan = row(2L, NOW.minusSeconds(20));
    NoteScheduleEntity broken = row(3L, NOW.minusSeconds(10));
    when(schedules.due(NOW, 50)).thenReturn(List.of(1L, 2L, 3L));
    when(schedules.findById(1L)).thenReturn(Optional.of(ok));
    when(schedules.findById(2L)).thenReturn(Optional.of(orphan));
    when(schedules.findById(3L)).thenReturn(Optional.of(broken));
    when(composer.write(7L, DRAFT, List.of()))
        .thenReturn(null)
        .thenThrow(new NoteException(NoteErrorCode.NOTE_NOT_FOUND, 9L))
        .thenThrow(new IllegalStateException("storage down"));

    assertThat(service().publishDue()).isEqualTo(1);

    verify(schedules).delete(ok);
    assertThat(orphan.getFailure()).isEqualTo("NOTE_NOT_FOUND");
    assertThat(broken.getFailure()).isEqualTo("NOTE_SCHEDULE_FAILED");
    verify(schedules, never()).delete(orphan);
  }

  @Test
  void schedulingAndPublishingAskStorageBeforeTheirTransactionsOpen() {
    List<String> seen = new ArrayList<>();
    when(images.verifyAll(7L, DRAFT.images()))
        .thenAnswer(
            inv -> {
              seen.add("storage " + FakeTransactions.where());
              return STORED;
            });
    doAnswer(
            inv -> {
              seen.add("check " + FakeTransactions.where());
              return null;
            })
        .when(composer)
        .validate(7L, DRAFT, STORED);
    when(composer.write(7L, DRAFT, STORED))
        .thenAnswer(
            inv -> {
              seen.add("write " + FakeTransactions.where());
              return null;
            });
    when(schedules.load(any(), any(), any())).thenReturn(new NoteScheduleRepository.Load(0, 0));
    when(schedules.save(any())).thenAnswer(inv -> inv.getArgument(0));
    NoteScheduleEntity due = row(1L, NOW.minusSeconds(30));
    when(schedules.due(NOW, 50)).thenReturn(List.of(1L));
    when(schedules.findById(1L)).thenReturn(Optional.of(due));

    service().schedule(7L, DRAFT, NOW.plus(Duration.ofHours(1)));
    assertThat(service().publishDue()).isEqualTo(1);

    assertThat(seen)
        .containsExactly("storage outside", "check inside", "storage outside", "write inside");
    verify(schedules).delete(due);
  }

  @Test
  void aDueNoteWhoseImageStorageTurnsAwayKeepsThatReasonAndNothingIsWritten() {
    NoteScheduleEntity gone = row(1L, NOW.minusSeconds(30));
    when(schedules.due(NOW, 50)).thenReturn(List.of(1L));
    when(schedules.findById(1L)).thenReturn(Optional.of(gone));
    when(images.verifyAll(7L, DRAFT.images()))
        .thenThrow(new NoteException(NoteErrorCode.NOTE_IMAGE_INVALID, "upload not found"));

    assertThat(service().publishDue()).isZero();

    assertThat(gone.getFailure()).isEqualTo("NOTE_IMAGE_INVALID");
    verify(composer, never()).write(any(), any(), any());
    verify(schedules, never()).delete(gone);
  }

  @Test
  void aNoteCancelledWhileItsImagesAreCheckedIsNotPosted() {
    NoteScheduleEntity cancelled = row(1L, NOW.minusSeconds(30));
    when(schedules.due(NOW, 50)).thenReturn(List.of(1L));
    when(schedules.findById(1L)).thenReturn(Optional.of(cancelled), Optional.empty());

    assertThat(service().publishDue()).isZero();

    verify(composer, never()).write(any(), any(), any());
    assertThat(cancelled.getFailure()).isNull();
  }

  @Test
  void aNoteScheduledByAnAccountSuspendedSinceKeepsThatReasonAndIsNotRetried() {
    NoteScheduleEntity held = row(1L, NOW.minusSeconds(30));
    when(schedules.due(NOW, 50)).thenReturn(List.of(1L));
    when(schedules.findById(1L)).thenReturn(Optional.of(held));
    when(composer.write(7L, DRAFT, List.of()))
        .thenThrow(new UserException(UserErrorCode.ACCOUNT_SUSPENDED));

    assertThat(service().publishDue()).isZero();

    assertThat(held.getFailure()).isEqualTo("ACCOUNT_SUSPENDED");
    verify(schedules, never()).delete(held);
  }

  @Test
  void aDeletedAccountsScheduledNotesGoWithIt() {
    service().forget(7L);

    verify(schedules).deleteAllForUser(7L);
  }
}
