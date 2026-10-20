package com.example.short_link.room.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_invite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomInviteEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "room_id", nullable = false)
  private Long roomId;

  @Column(name = "creator_id", nullable = false)
  private Long creatorId;

  @Column(nullable = false, length = 16)
  private String code;

  @Column(name = "short_code", length = 16)
  private String shortCode;

  @Column(name = "max_uses")
  private Integer maxUses;

  @Column(nullable = false)
  private int uses;

  @Column(nullable = false)
  private boolean approval;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "disabled_at")
  private Instant disabledAt;

  public RoomInviteEntity(
      Long roomId,
      Long creatorId,
      String code,
      Integer maxUses,
      boolean approval,
      Instant expiresAt) {
    this.roomId = roomId;
    this.creatorId = creatorId;
    this.code = code;
    this.maxUses = maxUses;
    this.approval = approval;
    this.expiresAt = expiresAt;
  }

  public void linkAs(String shortCode) {
    this.shortCode = shortCode;
  }

  public void disable(Instant at) {
    if (disabledAt == null) {
      disabledAt = at;
    }
  }

  public void use() {
    uses++;
  }

  public Optional<RoomInviteUnusable> unusableAt(Instant now) {
    if (disabledAt != null) {
      return Optional.of(RoomInviteUnusable.DISABLED);
    }
    if (expiresAt != null && !expiresAt.isAfter(now)) {
      return Optional.of(RoomInviteUnusable.EXPIRED);
    }
    if (maxUses != null && uses >= maxUses) {
      return Optional.of(RoomInviteUnusable.USED_UP);
    }
    return Optional.empty();
  }
}
