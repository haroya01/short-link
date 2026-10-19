package com.example.short_link.room.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEntity extends BaseTimeEntity {

  public static final int MAX_NAME_LENGTH = 50;
  public static final int MAX_MEMBERS = 50;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = MAX_NAME_LENGTH)
  private String name;

  @Column(name = "approval_required", nullable = false)
  private boolean approvalRequired;

  public RoomEntity(Long ownerId, String name) {
    this.ownerId = ownerId;
    this.name = name;
  }

  public boolean isOwnedBy(Long userId) {
    return ownerId.equals(userId);
  }

  public void rename(String name) {
    this.name = name;
  }

  public void askForApproval(boolean required) {
    this.approvalRequired = required;
  }

  public void handTo(Long ownerId) {
    this.ownerId = ownerId;
  }
}
