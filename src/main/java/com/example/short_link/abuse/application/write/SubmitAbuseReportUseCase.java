package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmitAbuseReportUseCase {

  private final AbuseReportRepository abuseReportRepository;

  @Transactional
  public AbuseReportEntity execute(SubmitAbuseReportCommand cmd) {
    return abuseReportRepository.save(
        new AbuseReportEntity(
            cmd.reporterUserId(), cmd.subjectType(), cmd.subjectId(), cmd.reason()));
  }
}
