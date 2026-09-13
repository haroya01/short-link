package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmitAbuseReportUseCase {

  private final AbuseReportRepository abuseReportRepository;
  private final AbuseSubjectReader subjects;

  @Transactional
  public AbuseReportEntity execute(SubmitAbuseReportCommand cmd) {
    if (!subjects.subjectExists(cmd.subjectType(), cmd.subjectId())) {
      throw new AbuseException(
              AbuseErrorCode.SUBJECT_NOT_FOUND, cmd.subjectType() + "#" + cmd.subjectId())
          .with("subjectType", cmd.subjectType().name())
          .with("subjectId", cmd.subjectId());
    }
    // 익명 신고는 신고자를 식별할 수 없어 중복 검사에서 제외한다.
    if (abuseReportRepository.existsOpenReport(
        cmd.reporterUserId(), cmd.subjectType(), cmd.subjectId())) {
      throw new AbuseException(AbuseErrorCode.DUPLICATE_REPORT)
          .with("subjectType", cmd.subjectType().name())
          .with("subjectId", cmd.subjectId());
    }
    return abuseReportRepository.save(
        new AbuseReportEntity(
            cmd.reporterUserId(),
            cmd.subjectType(),
            cmd.subjectId(),
            cmd.reasonCode(),
            cmd.detail()));
  }
}
