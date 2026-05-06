package com.example.short_link.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "click_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(name = "clicked_at", nullable = false, updatable = false)
  private Instant clickedAt;

  @Column(columnDefinition = "TEXT")
  private String referrer;

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

  @Builder
  public ClickEventEntity(
      Long linkId,
      String referrer,
      String userAgent,
      String clientIp,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmTerm,
      String utmContent,
      String deviceClass,
      String osName,
      String browserName,
      boolean bot) {
    this.linkId = linkId;
    this.referrer = referrer;
    this.userAgent = userAgent;
    this.clientIp = clientIp;
    this.utmSource = utmSource;
    this.utmMedium = utmMedium;
    this.utmCampaign = utmCampaign;
    this.utmTerm = utmTerm;
    this.utmContent = utmContent;
    this.deviceClass = deviceClass;
    this.osName = osName;
    this.browserName = browserName;
    this.bot = bot;
  }

  @PrePersist
  void prePersist() {
    this.clickedAt = Instant.now();
  }
}
