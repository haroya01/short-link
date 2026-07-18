package com.example.short_link.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 독자 행동 이벤트 한 줄 — {@code post_view_event}(도달)와 짝을 이루는 "행동" 계층. 스크롤 깊이({@code read_progress}), 두 번째
 * 행동({@code second_action}: 다른 글/연결/프로필/시리즈/태그 클릭), CTA 클릭을 세션 단위로 남긴다.
 *
 * <p>식별은 두 겹이다: {@code sessionId} 는 탭 수명(sessionStorage)의 임시 ID 로 퍼널 순서를 잇고, {@code visitorHash} 는
 * post_view_event 와 같은 공식(post+IP+UA)이라 같은 글의 도달 이벤트와 조인된다. 추적 쿠키는 쓰지 않으며 Sec-GPC 방문자는 해시를 만들지 않는다
 * — 조회 경로({@code RecordPostViewUseCase})와 같은 계약. 원본 행은 보존 기간(기본 90일)이 지나면 청소 잡이 걷는다.
 */
@Entity
@Table(name = "behavior_event")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BehaviorEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_name", nullable = false, updatable = false, length = 32)
  private String eventName;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "session_id", length = 40)
  private String sessionId;

  @Column(name = "post_id")
  private Long postId;

  @Column(name = "target_type", length = 32)
  private String targetType;

  @Column(name = "target_id", length = 64)
  private String targetId;

  @Column(name = "depth_pct")
  private Integer depthPct;

  @Column(name = "dwell_ms")
  private Long dwellMs;

  @Column(name = "device_class", length = 32)
  private String deviceClass;

  @Column(name = "is_bot", nullable = false)
  private boolean bot;

  @Column(name = "bot_name", length = 64)
  private String botName;

  @Column(name = "visitor_hash", length = 64)
  private String visitorHash;
}
