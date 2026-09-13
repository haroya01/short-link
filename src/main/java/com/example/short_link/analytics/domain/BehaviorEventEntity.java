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

/** 세션 ID는 탭 안의 행동 순서를 잇고, 방문자 해시는 글 조회와 조인하는 데 사용한다. Sec-GPC 요청에는 방문자 해시를 남기지 않는다. */
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
