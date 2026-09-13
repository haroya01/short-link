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

/** 집행 실패 시 처리 상태도 롤백되도록 두 변경을 같은 트랜잭션에서 수행한다. */
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
