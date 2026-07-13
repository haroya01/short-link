package com.example.short_link.abuse.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.exception.AbuseErrorCode;
import com.example.short_link.abuse.exception.AbuseException;
import org.junit.jupiter.api.Test;

/** 신·구 사유 계약 하위호환 매핑 검증. */
class SubmitAbuseReportRequestTest {

  private SubmitAbuseReportRequest req(String reasonCode, String detail, String reason) {
    return new SubmitAbuseReportRequest("POST", 42L, reasonCode, detail, reason);
  }

  @Test
  void newContract_usesReasonCodeAndDetailAsIs() {
    SubmitAbuseReportRequest r = req("SPAM", "도배 신고", null);

    assertThat(r.resolvedReasonCode()).isEqualTo(AbuseReason.SPAM);
    assertThat(r.resolvedDetail()).isEqualTo("도배 신고");
  }

  @Test
  void newContract_reasonCodeIsCaseInsensitiveAndTrimmed() {
    SubmitAbuseReportRequest r = req("  harassment  ", null, null);

    assertThat(r.resolvedReasonCode()).isEqualTo(AbuseReason.HARASSMENT);
    assertThat(r.resolvedDetail()).isNull();
  }

  @Test
  void legacyContract_freeTextReasonMapsToOtherAndAbsorbsIntoDetail() {
    SubmitAbuseReportRequest r = req(null, null, "이 링크는 피싱 사이트입니다");

    assertThat(r.resolvedReasonCode()).isEqualTo(AbuseReason.OTHER);
    assertThat(r.resolvedDetail()).isEqualTo("이 링크는 피싱 사이트입니다");
  }

  @Test
  void legacyContract_blankReasonCodeFallsBackToLegacyReason() {
    SubmitAbuseReportRequest r = req("   ", null, "스팸으로 보임");

    assertThat(r.resolvedReasonCode()).isEqualTo(AbuseReason.OTHER);
    assertThat(r.resolvedDetail()).isEqualTo("스팸으로 보임");
  }

  @Test
  void newContract_existingDetailIsPreservedOverLegacyReason() {
    // reasonCode 신규 경로에서는 옛 reason 을 상세로 흡수하지 않고, 신규 detail 을 유지한다.
    SubmitAbuseReportRequest r = req("COPYRIGHT", "원본 URL 첨부", "무시될 옛 텍스트");

    assertThat(r.resolvedReasonCode()).isEqualTo(AbuseReason.COPYRIGHT);
    assertThat(r.resolvedDetail()).isEqualTo("원본 URL 첨부");
  }

  @Test
  void bothMissing_throwsReasonRequired() {
    SubmitAbuseReportRequest r = req(null, null, null);

    assertThatThrownBy(r::resolvedReasonCode)
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.REASON_REQUIRED);
  }

  @Test
  void bothBlank_throwsReasonRequired() {
    SubmitAbuseReportRequest r = req("  ", null, "   ");

    assertThatThrownBy(r::resolvedReasonCode)
        .isInstanceOf(AbuseException.class)
        .extracting(e -> ((AbuseException) e).errorCode())
        .isEqualTo(AbuseErrorCode.REASON_REQUIRED);
  }
}
