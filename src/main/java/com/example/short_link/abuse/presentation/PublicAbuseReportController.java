package com.example.short_link.abuse.presentation;

import com.example.short_link.abuse.application.write.SubmitAbuseReportCommand;
import com.example.short_link.abuse.application.write.SubmitAbuseReportUseCase;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.presentation.request.SubmitAbuseReportRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 익명 사용자와 로그인 사용자 모두 신고할 수 있다. */
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
            AbuseSubjectType.valueOf(request.subjectType().toUpperCase(Locale.ROOT)),
            request.subjectId(),
            request.resolvedReasonCode(),
            request.resolvedDetail()));
  }
}
