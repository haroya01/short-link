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

  /**
   * 같은 신고자가 같은 대상에 대해 아직 열린(OPEN/REVIEWING) 신고를 갖고 있는지 — 중복 신고 가드용. 익명 신고 (reporterUserId=null)는 대상
   * 못하므로 가드 밖.
   */
  boolean existsOpenReport(Long reporterUserId, AbuseSubjectType subjectType, Long subjectId);
}
