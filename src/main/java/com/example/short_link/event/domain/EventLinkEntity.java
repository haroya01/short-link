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

/** primary 링크도 별칭 링크와 함께 한 행으로 저장한다. */
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
