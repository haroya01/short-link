package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResolveAbuseReportUseCase {

  private final AbuseReportRepository abuseReportRepository;

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

    switch (cmd.resolution()) {
      case REVIEWING -> report.markReviewing(cmd.adminNote());
      case RESOLVED -> report.resolve(cmd.adminNote());
      case REJECTED -> report.reject(cmd.adminNote());
    }

    return abuseReportRepository.save(report);
  }
}
