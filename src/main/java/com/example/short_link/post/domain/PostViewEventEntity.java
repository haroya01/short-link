package com.example.short_link.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Anonymous views are not deduplicated. The log supports rolling-window ranking while
 * posts.view_count holds the lifetime total. Dimensions may be null on legacy rows or when
 * enrichment fails, so view recording can continue.
 */
@Entity
@Table(name = "post_view_event")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostViewEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false, updatable = false)
  private Long postId;

  @Column(name = "viewed_at", nullable = false, updatable = false)
  private Instant viewedAt;

  @Column(columnDefinition = "TEXT")
  private String referrer;

  @Column(name = "referrer_host", length = 255)
  private String referrerHost;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "utm_source", length = 255)
  private String utmSource;

  @Column(name = "utm_medium", length = 255)
  private String utmMedium;

  @Column(name = "utm_campaign", length = 255)
  private String utmCampaign;

  @Column(name = "utm_term", length = 255)
  private String utmTerm;

  @Column(name = "utm_content", length = 255)
  private String utmContent;

  @Column(name = "device_class", length = 32)
  private String deviceClass;

  @Column(name = "os_name", length = 64)
  private String osName;

  @Column(name = "browser_name", length = 64)
  private String browserName;

  @Column(name = "is_bot", nullable = false)
  private boolean bot;

  @Column(name = "bot_name", length = 64)
  private String botName;

  @Column(name = "country_code", length = 2)
  private String countryCode;

  @Column(name = "region_name", length = 128)
  private String regionName;

  @Column(name = "city_name", length = 128)
  private String cityName;

  @Column(length = 8)
  private String language;

  @Column(name = "visitor_hash", length = 64)
  private String visitorHash;

  @Column(name = "source_channel", length = 64)
  private String sourceChannel;

  /** behavior_event와 퍼널 조인용 탭 수명 세션이다. 구형 행과 sid 없는 비콘은 null이다. */
  @Column(name = "session_id", length = 40)
  private String sessionId;

  public PostViewEventEntity(Long postId, Instant viewedAt) {
    this.postId = postId;
    this.viewedAt = viewedAt;
  }

  @PrePersist
  void prePersist() {
    if (this.viewedAt == null) {
      this.viewedAt = Instant.now();
    }
  }
}
