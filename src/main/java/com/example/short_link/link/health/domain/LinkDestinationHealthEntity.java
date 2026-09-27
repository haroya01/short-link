package com.example.short_link.link.health.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_destination_health")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkDestinationHealthEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "checked_url", nullable = false, length = 2048)
  private String checkedUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private DestinationFailure failure;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(nullable = false)
  private int failures;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @Column(name = "broken_since")
  private Instant brokenSince;

  public LinkDestinationHealthEntity(LinkId linkId) {
    this.linkId = linkId == null ? null : linkId.value();
  }

  public boolean isBrokenFor(String destination) {
    return brokenSince != null && destination != null && destination.equals(checkedUrl);
  }

  public void startCheckOf(String destination) {
    if (!destination.equals(checkedUrl)) {
      this.checkedUrl = destination;
      this.failure = null;
      this.httpStatus = null;
      this.failures = 0;
      this.brokenSince = null;
    }
  }

  public void markHealthy(Instant now) {
    this.failure = null;
    this.httpStatus = null;
    this.failures = 0;
    this.brokenSince = null;
    this.checkedAt = now;
  }

  public void markInconclusive(Instant now) {
    this.checkedAt = now;
  }

  public boolean markFailed(
      DestinationFailure failure, Integer httpStatus, Instant now, int confirmAfter) {
    this.failure = failure;
    this.httpStatus = httpStatus;
    this.failures++;
    this.checkedAt = now;
    if (brokenSince == null && failures >= confirmAfter) {
      brokenSince = now;
      return true;
    }
    return false;
  }
}
