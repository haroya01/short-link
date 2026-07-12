package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubmitAbuseReportUseCase {

  private final AbuseReportRepository abuseReportRepository;

  @Transactional
  public AbuseReportEntity execute(SubmitAbuseReportCommand cmd) {
    // 존재검사: 없는 대상 신고는 거부(400/404). 오타·정리된 대상으로 큐를 오염시키지 않는다.
    if (!abuseReportRepository.subjectExists(cmd.subjectType(), cmd.subjectId())) {
      throw new AbuseException(
              AbuseErrorCode.SUBJECT_NOT_FOUND, cmd.subjectType() + "#" + cmd.subjectId())
          .with("subjectType", cmd.subjectType().name())
          .with("subjectId", cmd.subjectId());
    }
    // 중복 신고 가드: 로그인 신고자가 같은 대상에 대해 이미 열린(OPEN/REVIEWING) 신고를 갖고 있으면 거부.
    // 익명 신고는 신고자 식별이 없어 가드 밖(existsOpenReport 가 null 이면 false 반환).
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
