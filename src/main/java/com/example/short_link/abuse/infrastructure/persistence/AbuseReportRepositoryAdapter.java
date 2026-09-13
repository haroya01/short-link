package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AbuseReportRepositoryAdapter implements AbuseReportRepository {

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
}
