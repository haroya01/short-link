package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.ModerationAction;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import com.example.short_link.common.post.CommentModerationPort;
import com.example.short_link.common.post.PostModerationPort;
import com.example.short_link.common.user.UserModerationPort;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 처리 + 집행. 상태 전이(REVIEWING/RESOLVED/REJECTED)와 선택적 집행 조치(글 게시취소·댓글 soft삭제·유저 정지/차단)를 <b>같은
 * 트랜잭션</b>에서 수행한다 — 집행이 실패하면 상태 전이도 롤백되어 "처리됐는데 집행 안 됨"이 남지 않는다. 크로스슬라이스 집행은 전부 common 중립 포트({@link
 * PostModerationPort}·{@link CommentModerationPort}·{@link UserModerationPort})를 경유해 슬라이스 그래프를
 * 비순환으로 유지한다(ArchUnit 강제).
 */
@Service
@RequiredArgsConstructor
public class ResolveAbuseReportUseCase {

  private final AbuseReportRepository abuseReportRepository;
  private final PostModerationPort postModerationPort;
  private final CommentModerationPort commentModerationPort;
  private final UserModerationPort userModerationPort;

  @Transactional
  public AbuseReportEntity execute(ResolveAbuseReportCommand cmd) {
    AbuseReportEntity report =
        abuseReportRepository
            .findById(cmd.reportId())
            .orElseThrow(
                () -> new AbuseException(AbuseErrorCode.ABUSE_REPORT_NOT_FOUND, cmd.reportId()));

    if (report.getStatus() == AbuseReportStatus.RESOLVED
        || report.getStatus() == AbuseReportStatus.REJECTED) {
      throw new AbuseException(AbuseErrorCode.ALREADY_RESOLVED, cmd.reportId());
    }

    // 집행 먼저 — 실패 시 상태 전이까지 롤백. 조치는 신고 대상(subjectType/subjectId)에만 적용된다.
    enforce(cmd, report);

    switch (cmd.resolution()) {
      case REVIEWING -> report.markReviewing(cmd.adminNote());
      case RESOLVED -> report.resolve(cmd.adminNote());
      case REJECTED -> report.reject(cmd.adminNote());
    }

    return abuseReportRepository.save(report);
  }

  private void enforce(ResolveAbuseReportCommand cmd, AbuseReportEntity report) {
    ModerationAction action = cmd.action();
    if (action == ModerationAction.NONE) {
      return;
    }
    if (!action.appliesTo(report.getSubjectType())) {
      throw new AbuseException(
              AbuseErrorCode.ACTION_SUBJECT_MISMATCH, action + " vs " + report.getSubjectType())
          .with("action", action.name())
          .with("subjectType", report.getSubjectType().name());
    }
    Long adminUserId = cmd.adminUserId();
    Long subjectId = report.getSubjectId();
    switch (action) {
      case UNPUBLISH_POST -> postModerationPort.unpublish(adminUserId, subjectId);
      case DELETE_COMMENT -> commentModerationPort.softDelete(adminUserId, subjectId);
      case SUSPEND_USER -> userModerationPort.suspend(adminUserId, subjectId, requireFuture(cmd));
      case BAN_USER -> userModerationPort.ban(adminUserId, subjectId);
      case NONE -> {
        // 위에서 걸러짐 — switch 완전성 위한 no-op.
      }
    }
  }

  private Instant requireFuture(ResolveAbuseReportCommand cmd) {
    Instant until = cmd.suspendUntil();
    if (until == null || !until.isAfter(Instant.now())) {
      throw new AbuseException(AbuseErrorCode.SUSPEND_REQUIRES_EXPIRY);
    }
    return until;
  }
}
