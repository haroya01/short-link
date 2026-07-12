package com.example.short_link.abuse.presentation;

import com.example.short_link.abuse.application.write.SubmitAbuseReportCommand;
import com.example.short_link.abuse.application.write.SubmitAbuseReportUseCase;
import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.presentation.request.SubmitAbuseReportRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public abuse report submission. 인증 없이 (anonymous) 또는 로그인 user 둘 다 가능. CAPTCHA / PoW 게이트는 별도 트랙.
 * v0 는 단순 record.
 */
@RestController
@RequestMapping("/api/v1/public/abuse-reports")
@RequiredArgsConstructor
public class PublicAbuseReportController {

  private final SubmitAbuseReportUseCase submitAbuseReport;

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void submit(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody SubmitAbuseReportRequest request) {
    submitAbuseReport.execute(
        new SubmitAbuseReportCommand(
            userId,
            AbuseSubjectType.valueOf(request.subjectType().toUpperCase()),
            request.subjectId(),
            AbuseReason.valueOf(request.reasonCode().toUpperCase()),
            request.detail()));
  }
}
