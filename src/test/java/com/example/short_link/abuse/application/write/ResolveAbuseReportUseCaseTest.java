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
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.ModerationAction;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import com.example.short_link.common.link.LinkModerationPort;
import com.example.short_link.common.note.NoteModerationPort;
import com.example.short_link.common.post.CommentModerationPort;
import com.example.short_link.common.post.HighlightReplyModerationPort;
import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.common.user.UserModerationPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolveAbuseReportUseCaseTest {

  @Mock private AbuseReportRepository abuseReportRepository;
  @Mock private PostModerationPort postModerationPort;
  @Mock private CommentModerationPort commentModerationPort;
  @Mock private HighlightReplyModerationPort highlightReplyModerationPort;
  @Mock private NoteModerationPort noteModerationPort;
  @Mock private UserModerationPort userModerationPort;
  @Mock private LinkModerationPort linkModerationPort;

  private ResolveAbuseReportUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new ResolveAbuseReportUseCase(
            abuseReportRepository,
            postModerationPort,
            commentModerationPort,
            highlightReplyModerationPort,
            noteModerationPort,
            userModerationPort,
            linkModerationPort);
  }

  private AbuseReportEntity postReport() {
    return new AbuseReportEntity(7L, AbuseSubjectType.POST, 42L, AbuseReason.SPAM, "도배");
  }

  private AbuseReportEntity commentReport() {
    return new AbuseReportEntity(7L, AbuseSubjectType.COMMENT, 55L, AbuseReason.HARASSMENT, null);
  }

  private AbuseReportEntity userReport() {
    return new AbuseReportEntity(7L, AbuseSubjectType.USER, 99L, AbuseReason.HARASSMENT, null);
  }

  private ResolveAbuseReportCommand cmd(
      Long id, ResolveAbuseReportCommand.Resolution res, ModerationAction action, Instant until) {
    return new ResolveAbuseReportCommand(id, 1L, res, action, until, "note");
  }

  @Test
  void resolveWithoutActionTransitionsOnly() {
    AbuseReportEntity r = postReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity result =
        useCase.execute(
            cmd(1L, ResolveAbuseReportCommand.Resolution.RESOLVED, ModerationAction.NONE, null));

    assertThat(result.getStatus()).isEqualTo(AbuseReportStatus.RESOLVED);
    assertThat(result.getResolvedAt()).isNotNull();
    verifyNoInteractions(postModerationPort, commentModerationPort, userModerationPort);
  }

  @Test
  void unpublishPostActionCallsPortInSameFlow() {
    AbuseReportEntity r = postReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(
            1L,
            ResolveAbuseReportCommand.Resolution.RESOLVED,
            ModerationAction.UNPUBLISH_POST,
            null));

    verify(postModerationPort).takeDown(1L, 42L);
  }

  @Test
  void disableLinkActionSwitchesTheReportedLinkOff() {
    AbuseReportEntity r =
        new AbuseReportEntity(7L, AbuseSubjectType.LINK, 55L, AbuseReason.PHISHING, "가짜 로그인");
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(
            1L,
            ResolveAbuseReportCommand.Resolution.RESOLVED,
            ModerationAction.DISABLE_LINK,
            null));

    verify(linkModerationPort).disable(1L, 55L);
    verifyNoInteractions(postModerationPort, commentModerationPort, userModerationPort);
  }

  @Test
  void disableLinkCannotTargetAPost() {
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(postReport()));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    cmd(
                        1L,
                        ResolveAbuseReportCommand.Resolution.RESOLVED,
                        ModerationAction.DISABLE_LINK,
                        null)))
        .isInstanceOf(AbuseException.class);
    verifyNoInteractions(linkModerationPort);
  }

  @Test
  void deleteCommentActionCallsPort() {
    AbuseReportEntity r = commentReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(
            1L,
            ResolveAbuseReportCommand.Resolution.RESOLVED,
            ModerationAction.DELETE_COMMENT,
            null));

    verify(commentModerationPort).softDelete(1L, 55L);
  }

  @Test
  void deleteNoteActionTakesTheNoteDownAndOnlyFitsANote() {
    AbuseReportEntity r =
        new AbuseReportEntity(7L, AbuseSubjectType.NOTE, 66L, AbuseReason.SPAM, null);
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(1L, ResolveAbuseReportCommand.Resolution.RESOLVED, ModerationAction.DELETE_NOTE, null));

    verify(noteModerationPort).takeDown(1L, 66L);
    assertThat(ModerationAction.DELETE_NOTE.appliesTo(AbuseSubjectType.POST)).isFalse();
    assertThat(ModerationAction.DELETE_COMMENT.appliesTo(AbuseSubjectType.NOTE)).isFalse();
  }

  @Test
  void deleteHighlightReplyActionHidesTheReplyAndOnlyFitsAHighlightReply() {
    AbuseReportEntity r =
        new AbuseReportEntity(7L, AbuseSubjectType.HIGHLIGHT_REPLY, 77L, AbuseReason.SPAM, null);
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(
            1L,
            ResolveAbuseReportCommand.Resolution.RESOLVED,
            ModerationAction.DELETE_HIGHLIGHT_REPLY,
            null));

    verify(highlightReplyModerationPort).softDelete(1L, 77L);
    verifyNoInteractions(commentModerationPort);
    assertThat(ModerationAction.DELETE_HIGHLIGHT_REPLY.appliesTo(AbuseSubjectType.COMMENT))
        .isFalse();
    assertThat(ModerationAction.DELETE_COMMENT.appliesTo(AbuseSubjectType.HIGHLIGHT_REPLY))
        .isFalse();
  }

  @Test
  void suspendUserActionCallsPortWithExpiry() {
    AbuseReportEntity r = userReport();
    Instant until = Instant.now().plus(7, ChronoUnit.DAYS);
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(
            1L,
            ResolveAbuseReportCommand.Resolution.RESOLVED,
            ModerationAction.SUSPEND_USER,
            until));

    verify(userModerationPort).suspend(1L, 99L, until);
  }

  @Test
  void banUserActionCallsPort() {
    AbuseReportEntity r = userReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(
        cmd(1L, ResolveAbuseReportCommand.Resolution.RESOLVED, ModerationAction.BAN_USER, null));

    verify(userModerationPort).ban(1L, 99L);
  }

  @Test
  void rejectsActionMismatchedToSubject() {
    AbuseReportEntity r = postReport(); // POST 대상에 BAN_USER 는 부적합
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    cmd(
                        1L,
                        ResolveAbuseReportCommand.Resolution.RESOLVED,
                        ModerationAction.BAN_USER,
                        null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.ACTION_SUBJECT_MISMATCH);

    verify(abuseReportRepository, never()).save(any());
    verifyNoInteractions(userModerationPort);
  }

  @Test
  void suspendWithoutFutureExpiryRejected() {
    AbuseReportEntity r = userReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    cmd(
                        1L,
                        ResolveAbuseReportCommand.Resolution.RESOLVED,
                        ModerationAction.SUSPEND_USER,
                        Instant.now().minusSeconds(60))))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.SUSPEND_REQUIRES_EXPIRY);

    verify(abuseReportRepository, never()).save(any());
    verify(userModerationPort, never()).suspend(any(), any(), any());
  }

  @Test
  void reviewingDoesNotStampResolved() {
    AbuseReportEntity r = postReport();
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));
    when(abuseReportRepository.save(any(AbuseReportEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    AbuseReportEntity result =
        useCase.execute(
            cmd(1L, ResolveAbuseReportCommand.Resolution.REVIEWING, ModerationAction.NONE, null));

    assertThat(result.getStatus()).isEqualTo(AbuseReportStatus.REVIEWING);
    assertThat(result.getResolvedAt()).isNull();
  }

  @Test
  void rejectsResolveOfAlreadyResolved() {
    AbuseReportEntity r = postReport();
    r.resolve("done");
    when(abuseReportRepository.findById(1L)).thenReturn(Optional.of(r));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    cmd(
                        1L,
                        ResolveAbuseReportCommand.Resolution.RESOLVED,
                        ModerationAction.NONE,
                        null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.ALREADY_RESOLVED);
  }

  @Test
  void notFoundThrows() {
    when(abuseReportRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    cmd(
                        99L,
                        ResolveAbuseReportCommand.Resolution.RESOLVED,
                        ModerationAction.NONE,
                        null)))
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.ABUSE_REPORT_NOT_FOUND);

    verify(postModerationPort, never()).takeDown(eq(1L), any());
  }
}
