package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One block edge: {@code blockerId} blocks {@code blockedId}. The pair is unique. */
@Entity
@Table(
    name = "user_block",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_user_block",
            columnNames = {"blocker_id", "blocked_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlockEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "blocker_id", nullable = false)
  private Long blockerId;

  @Column(name = "blocked_id", nullable = false)
  private Long blockedId;

  public UserBlockEntity(Long blockerId, Long blockedId) {
    this.blockerId = blockerId;
    this.blockedId = blockedId;
  }
}
