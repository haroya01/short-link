package com.example.short_link.abuse.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import com.example.short_link.common.event.NoteReportForwardRequested;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SubmitAbuseReportUseCaseTest {

  @Mock private AbuseReportRepository abuseReportRepository;
  @Mock private AbuseSubjectReader subjects;
  @Mock private ApplicationEventPublisher events;

  private SubmitAbuseReportUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SubmitAbuseReportUseCase(abuseReportRepository, subjects, events);
  }

  @Test
  void submitsReportWithHybridReason() {
    when(subjects.subjectExists(AbuseSubjectType.POST, 42L)).thenReturn(true);
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity saved =
        useCase.execute(
            new SubmitAbuseReportCommand(7L, AbuseSubjectType.POST, 42L, AbuseReason.SPAM, "도배"));

    assertThat(saved.getReporterUserId()).isEqualTo(7L);
    assertThat(saved.getSubjectType()).isEqualTo(AbuseSubjectType.POST);
    assertThat(saved.getSubjectId()).isEqualTo(42L);
    assertThat(saved.getReasonCode()).isEqualTo(AbuseReason.SPAM);
    assertThat(saved.getDetail()).isEqualTo("도배");
  }

  @Test
  void aPastedShortLinkBecomesALinkReport() {
    when(subjects.findLinkIdByShortCode("abc123")).thenReturn(Optional.of(55L));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity saved =
        useCase.executeForLink(
            null, "https://kurl.me/abc123?src=sms", AbuseReason.PHISHING, "은행 사칭");

    assertThat(saved.getReporterUserId()).isNull();
    assertThat(saved.getSubjectType()).isEqualTo(AbuseSubjectType.LINK);
    assertThat(saved.getSubjectId()).isEqualTo(55L);
    assertThat(saved.getReasonCode()).isEqualTo(AbuseReason.PHISHING);
  }

  @Test
  void aLinkThatDoesNotExistCannotBeReported() {
    when(subjects.findLinkIdByShortCode("zzz999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.executeForLink(null, "kurl.me/zzz999", AbuseReason.SPAM, null))
        .isInstanceOf(AbuseException.class)
        .satisfies(
            e ->
                assertThat(((AbuseException) e).errorCode())
                    .isEqualTo(AbuseErrorCode.SUBJECT_NOT_FOUND));
    verifyNoInteractions(abuseReportRepository);
  }

  @Test
  void textThatIsNotAShortLinkIsNotLookedUp() {
    assertThatThrownBy(() -> useCase.executeForLink(null, "kurl.me", AbuseReason.SPAM, null))
        .isInstanceOf(AbuseException.class);
    verifyNoInteractions(subjects, abuseReportRepository);
  }

  @Test
  void submitsAnonymousReportWithoutDetail() {
    when(subjects.subjectExists(AbuseSubjectType.USER, 9L)).thenReturn(true);
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity saved =
        useCase.execute(
            new SubmitAbuseReportCommand(
                null, AbuseSubjectType.USER, 9L, AbuseReason.HARASSMENT, null));

    assertThat(saved.getReporterUserId()).isNull();
    assertThat(saved.getReasonCode()).isEqualTo(AbuseReason.HARASSMENT);
    assertThat(saved.getDetail()).isNull();
  }

  @Test
  void rejectsMissingSubject() {
    when(subjects.subjectExists(AbuseSubjectType.POST, 404L)).thenReturn(false);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SubmitAbuseReportCommand(
                        7L, AbuseSubjectType.POST, 404L, AbuseReason.SPAM, null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.SUBJECT_NOT_FOUND);

    verify(abuseReportRepository, never()).save(any());
  }

  @Test
  void rejectsDuplicateOpenReport() {
    when(subjects.subjectExists(AbuseSubjectType.POST, 42L)).thenReturn(true);
    when(abuseReportRepository.existsOpenReport(7L, AbuseSubjectType.POST, 42L)).thenReturn(true);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new SubmitAbuseReportCommand(
                        7L, AbuseSubjectType.POST, 42L, AbuseReason.SPAM, null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.DUPLICATE_REPORT);

    verify(abuseReportRepository, never()).save(any());
  }

  @Test
  void anonymousReportSkipsDedupGuard() {
    // 익명(reporter=null)은 existsOpenReport 가 false 를 돌려주므로 중복 가드에 걸리지 않는다.
    when(subjects.subjectExists(AbuseSubjectType.POST, 42L)).thenReturn(true);
    when(abuseReportRepository.existsOpenReport(eq(null), any(), any())).thenReturn(false);
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new SubmitAbuseReportCommand(null, AbuseSubjectType.POST, 42L, AbuseReason.SPAM, null));

    verify(abuseReportRepository).save(any(AbuseReportEntity.class));
  }

  @Test
  void onlyANoteReportAskedToBeForwardedGoesOnToItsServer() {
    when(subjects.subjectExists(AbuseSubjectType.NOTE, 9L)).thenReturn(true);
    when(subjects.subjectExists(AbuseSubjectType.POST, 42L)).thenReturn(true);
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        new SubmitAbuseReportCommand(
            7L, AbuseSubjectType.NOTE, 9L, AbuseReason.SPAM, "광고 계정", true));
    useCase.execute(
        new SubmitAbuseReportCommand(8L, AbuseSubjectType.NOTE, 9L, AbuseReason.SPAM, null));
    useCase.execute(
        new SubmitAbuseReportCommand(7L, AbuseSubjectType.POST, 42L, AbuseReason.SPAM, null, true));

    verify(events).publishEvent(new NoteReportForwardRequested(9L, "광고 계정"));
    verify(events, org.mockito.Mockito.times(1)).publishEvent(any(Object.class));
  }
}
