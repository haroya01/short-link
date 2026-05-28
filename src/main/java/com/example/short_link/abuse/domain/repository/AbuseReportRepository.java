package com.example.short_link.abuse.domain.repository;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import java.util.List;
import java.util.Optional;

public interface AbuseReportRepository {

  AbuseReportEntity save(AbuseReportEntity report);

  Optional<AbuseReportEntity> findById(Long id);

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();
}
