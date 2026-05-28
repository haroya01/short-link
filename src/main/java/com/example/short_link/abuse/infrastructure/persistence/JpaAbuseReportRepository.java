package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAbuseReportRepository extends JpaRepository<AbuseReportEntity, Long> {

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();
}
