package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAbuseReportRepository extends JpaRepository<AbuseReportEntity, Long> {

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  boolean existsByReporterUserIdAndSubjectTypeAndSubjectIdAndStatusIn(
      Long reporterUserId,
      AbuseSubjectType subjectType,
      Long subjectId,
      Collection<AbuseReportStatus> statuses);
}
