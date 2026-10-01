package com.example.short_link.link.moderation.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
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

// A row means the link is switched off; switching it back on deletes the row.
@Entity
@Table(name = "link_moderation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkModerationEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LinkDisableReason reason;

  @Column(name = "disabled_by")
  private Long disabledBy;

  @Column(name = "disabled_at", nullable = false)
  private Instant disabledAt;

  public LinkModerationEntity(
      Long linkId, LinkDisableReason reason, Long disabledBy, Instant disabledAt) {
    this.linkId = linkId;
    this.reason = reason;
    this.disabledBy = disabledBy;
    this.disabledAt = disabledAt;
  }
}
