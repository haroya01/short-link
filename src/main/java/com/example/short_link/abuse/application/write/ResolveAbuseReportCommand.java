package com.example.short_link.abuse.application.write;

import com.example.short_link.abuse.domain.ModerationAction;
import java.time.Instant;

/**
 * 신고 처리 커맨드 — 상태 전이({@code resolution}) + 선택적 집행({@code action}). action 이 NONE 이 아니면 같은 트랜잭션에서 대상
 * 슬라이스에 실제 조치를 적용한다. {@code adminUserId} 는 집행 감사 로그용, {@code suspendUntil} 은 SUSPEND_USER 전용.
 */
public record ResolveAbuseReportCommand(
    Long reportId,
    Long adminUserId,
    Resolution resolution,
    ModerationAction action,
    Instant suspendUntil,
    String adminNote) {

  public enum Resolution {
    REVIEWING,
    RESOLVED,
    REJECTED
  }

  public ResolveAbuseReportCommand {
    if (reportId == null) throw new IllegalArgumentException("reportId required");
    if (resolution == null) throw new IllegalArgumentException("resolution required");
    if (action == null) action = ModerationAction.NONE;
    if (adminNote != null && adminNote.length() > 2000) {
      throw new IllegalArgumentException("adminNote max 2000");
    }
  }
}
