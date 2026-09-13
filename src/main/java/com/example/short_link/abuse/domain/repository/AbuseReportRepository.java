package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import java.util.List;
import java.util.Optional;

public interface AbuseReportRepository {

  AbuseReportEntity save(AbuseReportEntity report);

  Optional<AbuseReportEntity> findById(Long id);

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  /** OPEN/REVIEWING 신고만 중복으로 간주한다. 익명 신고자는 false를 반환한다. */
  boolean existsOpenReport(Long reporterUserId, AbuseSubjectType subjectType, Long subjectId);
}
