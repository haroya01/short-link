package com.example.short_link.abuse.presentation.request;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 제출 요청. 사유 하이브리드: {@code reasonCode}(정형 6종) + {@code detail}(자유서술, 선택·2000자). {@code
 * subjectType}/{@code reasonCode} 는 문자열로 받고 컨트롤러가 enum 으로 변환한다.
 *
 * <p>하위호환: 옛 클라이언트(iOS/웹)는 정형 코드 없이 자유서술 {@code reason} 만 보낸다. 그 경우 사유는 {@link AbuseReason#OTHER}
 * 로, 옛 {@code reason} 텍스트는 {@code detail} 로 흡수한다({@code detail} 이 이미 있으면 신규 값을 유지). 신·구 필드가 모두 없으면
 * 사유 누락으로 400. 신규 계약({@code reasonCode}+{@code detail})은 그대로 처리한다.
 */
public record SubmitAbuseReportRequest(
    @NotBlank String subjectType,
    @NotNull Long subjectId,
    String reasonCode,
    @Size(max = 2000) String detail,
    @Size(max = 2000) String reason) {

  /**
   * 신·구 필드를 합쳐 최종 사유 코드를 정한다. {@code reasonCode} 가 있으면 그대로, 없고 옛 {@code reason} 이 있으면 {@link
   * AbuseReason#OTHER}. 둘 다 없으면 400.
   */
  public AbuseReason resolvedReasonCode() {
    if (reasonCode != null && !reasonCode.isBlank()) {
      return AbuseReason.valueOf(reasonCode.trim().toUpperCase());
    }
    if (reason != null && !reason.isBlank()) {
      return AbuseReason.OTHER;
    }
    throw new AbuseException(AbuseErrorCode.REASON_REQUIRED);
  }

  /**
   * 최종 상세. 신규 {@code detail} 이 있으면 유지, 없고 옛 {@code reason}(자유서술) 만 있으면 그 텍스트를 상세로 흡수한다. {@code
   * reasonCode} 신규 경로에서 {@code reason} 은 무시한다.
   */
  public String resolvedDetail() {
    if (detail != null && !detail.isBlank()) {
      return detail;
    }
    boolean legacyReasonPath = reasonCode == null || reasonCode.isBlank();
    if (legacyReasonPath && reason != null && !reason.isBlank()) {
      return reason;
    }
    return detail;
  }
}
