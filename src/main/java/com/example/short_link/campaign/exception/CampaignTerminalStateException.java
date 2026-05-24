package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** ENDED 또는 ARCHIVED 상태에선 batch 추가/수정이 거부된다 — 인쇄된 QR 의 동작이 사용자 모르는 사이 바뀌면 안 됨. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CampaignTerminalStateException extends RuntimeException {
  public CampaignTerminalStateException() {
    super("campaign is ended or archived");
  }
}
