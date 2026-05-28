package com.example.short_link.campaign.application.dto;

import com.example.short_link.campaign.application.helper.QrPngEncoder;

/**
 * 다운로드 직전 사용자가 정하는 QR 옵션. Campaign / Batch 도메인엔 저장 안 함 — 같은 캠페인 자산을 여러 옵션으로 여러 번 뽑을 수 있어야 하기 때문.
 */
public record QrOptions(int sizePx, QrPngEncoder.Ec ec, boolean includeLabel) {

  public QrOptions {
    if (sizePx < 128 || sizePx > 4096) {
      throw new IllegalArgumentException("sizePx must be 128–4096");
    }
    if (ec == null) {
      ec = QrPngEncoder.Ec.M;
    }
  }
}
