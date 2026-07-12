package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import java.time.Instant;

/**
 * 관리자 모더레이션 큐 뷰. 사유는 하이브리드({@code reasonCode} 정형 + {@code detail} 자유서술)이고, 대상 스냅샷은 subjectType 별로
 * 채워진다 — POST(제목·URL), COMMENT(본문 발췌·작성자), USER(핸들). {@code subjectRemoved} 는 대상이 이미 내려갔는지(글
 * 게시취소·댓글 soft삭제) 표시. 스냅샷을 못 채우면(대상 없음) null/false 로 남아 클라이언트가 {@code TYPE #id} 폴백으로 렌더한다.
 */
public record AbuseReportView(
    Long id,
    Long reporterUserId,
    String subjectType,
    Long subjectId,
    String reasonCode,
    String detail,
    String status,
    String adminNote,
    Instant createdAt,
    Instant resolvedAt,
    String subjectTitle,
    String subjectAuthorHandle,
    String subjectUrl,
    String subjectExcerpt,
    boolean subjectRemoved) {

  /** 대상 종류별 하이드레이션 스냅샷. 채우지 못한 필드는 null/false. */
  public record SubjectSnapshot(
      String title, String authorHandle, String url, String excerpt, boolean removed) {

    public static final SubjectSnapshot EMPTY = new SubjectSnapshot(null, null, null, null, false);
  }

  /** 스냅샷 없는 기본 뷰 — 대상을 하이드레이션하지 않는 호출자용. */
  public static AbuseReportView from(AbuseReportEntity report) {
    return of(report, SubjectSnapshot.EMPTY);
  }

  public static AbuseReportView of(AbuseReportEntity report, SubjectSnapshot snapshot) {
    return new AbuseReportView(
        report.getId(),
        report.getReporterUserId(),
        report.getSubjectType().name(),
        report.getSubjectId(),
        report.getReasonCode() == null ? null : report.getReasonCode().name(),
        report.getDetail(),
        report.getStatus().name(),
        report.getAdminNote(),
        report.getCreatedAt(),
        report.getResolvedAt(),
        snapshot.title(),
        snapshot.authorHandle(),
        snapshot.url(),
        snapshot.excerpt(),
        snapshot.removed());
  }
}
