package com.example.short_link.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이벤트의 배포 채널별 별칭 단축링크 ("단톡용", "QR용" …). primary 링크도 한 행. */
@Entity
@Table(name = "event_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private Long eventId;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(nullable = false, length = 50)
  private String label;

  public EventLinkEntity(Long eventId, Long linkId, String label) {
    this.eventId = eventId;
    this.linkId = linkId;
    this.label = label;
  }
}
