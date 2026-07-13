package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AbuseReportRepositoryAdapter implements AbuseReportRepository {

  // "열린" 신고 — 아직 종결(RESOLVED/REJECTED)되지 않은 상태. 이 상태 집합에서만 중복을 막는다.
  private static final List<AbuseReportStatus> OPEN_STATUSES =
      List.of(AbuseReportStatus.OPEN, AbuseReportStatus.REVIEWING);

  private final JpaAbuseReportRepository jpa;

  @Override
  public AbuseReportEntity save(AbuseReportEntity report) {
    return jpa.save(report);
  }

  @Override
  public Optional<AbuseReportEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status) {
    return jpa.findAllByStatusOrderByCreatedAtDesc(status);
  }

  @Override
  public List<AbuseReportEntity> findAllByOrderByCreatedAtDesc() {
    return jpa.findAllByOrderByCreatedAtDesc();
  }

  @Override
  public boolean existsOpenReport(
      Long reporterUserId, AbuseSubjectType subjectType, Long subjectId) {
    if (reporterUserId == null) {
      return false;
    }
    return jpa.existsByReporterUserIdAndSubjectTypeAndSubjectIdAndStatusIn(
        reporterUserId, subjectType, subjectId, OPEN_STATUSES);
  }

  @Override
  public List<PostSubjectSnapshot> findPostSubjectSnapshots(Collection<Long> postIds) {
    return jpa.findPostSubjectSnapshots(postIds);
  }

  @Override
  public List<CommentSubjectSnapshot> findCommentSubjectSnapshots(Collection<Long> commentIds) {
    return jpa.findCommentSubjectSnapshots(commentIds);
  }

  @Override
  public List<UserSubjectSnapshot> findUserSubjectSnapshots(Collection<Long> userIds) {
    return jpa.findUserSubjectSnapshots(userIds);
  }

  @Override
  public boolean subjectExists(AbuseSubjectType subjectType, Long subjectId) {
    if (subjectId == null) {
      return false;
    }
    return switch (subjectType) {
      case POST -> jpa.countPostById(subjectId) > 0;
      case COMMENT -> jpa.countCommentById(subjectId) > 0;
      case USER -> jpa.countUserById(subjectId) > 0;
    };
  }
}
