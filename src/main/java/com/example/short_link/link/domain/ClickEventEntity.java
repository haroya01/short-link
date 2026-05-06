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

  public ClickEventEntity(Long linkId, String referrer, String userAgent, String clientIp) {
    this.linkId = linkId;
    this.referrer = referrer;
    this.userAgent = userAgent;
    this.clientIp = clientIp;
  }

  @PrePersist
  void prePersist() {
    this.clickedAt = Instant.now();
  }
}
