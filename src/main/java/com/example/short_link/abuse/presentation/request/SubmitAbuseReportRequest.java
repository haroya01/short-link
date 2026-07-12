package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 제출 요청. 사유 하이브리드: {@code reasonCode}(정형 6종, 필수) + {@code detail}(자유서술, 선택·2000자). {@code
 * subjectType}/{@code reasonCode} 는 문자열로 받고 컨트롤러가 enum 으로 변환한다.
 */
public record SubmitAbuseReportRequest(
    @NotBlank String subjectType,
    @NotNull Long subjectId,
    @NotBlank String reasonCode,
    @Size(max = 2000) String detail) {}
