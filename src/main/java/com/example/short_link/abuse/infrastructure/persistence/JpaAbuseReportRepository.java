package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.ReasonTally;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAbuseReportRepository extends JpaRepository<AbuseReportEntity, Long> {

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  boolean existsByReporterUserIdAndSubjectTypeAndSubjectIdAndStatusIn(
      Long reporterUserId,
      AbuseSubjectType subjectType,
      Long subjectId,
      Collection<AbuseReportStatus> statuses);

  @Query(
      "SELECT r.reasonCode AS reason, r.status AS status, COUNT(r) AS reports "
          + "FROM AbuseReportEntity r "
          + "WHERE r.subjectType = :subjectType AND r.subjectId = :subjectId "
          + "GROUP BY r.reasonCode, r.status")
  List<ReasonTally> tallyBySubject(
      @Param("subjectType") AbuseSubjectType subjectType, @Param("subjectId") Long subjectId);
}
