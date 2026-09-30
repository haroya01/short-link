package com.example.short_link.profile.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.profile.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileBlockEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "block_type", nullable = false, length = 16)
  private ProfileBlockType type;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "profile_order", nullable = false)
  private Integer profileOrder;

  @Column(name = "profile_highlighted", nullable = false)
  private boolean profileHighlighted;

  public ProfileBlockEntity(Long userId, ProfileBlockType type, String content, int profileOrder) {
    this.userId = userId;
    this.type = type;
    this.content = content;
    this.profileOrder = profileOrder;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public void updateContent(String content) {
    this.content = content;
  }

  public void setProfileOrder(int profileOrder) {
    this.profileOrder = profileOrder;
  }

  /** 대표로 올릴 수 있는 블록 — 방문자가 바로 행동할 수 있는 모집·상품만. */
  public boolean isHighlightable() {
    return type == ProfileBlockType.EVENT || type == ProfileBlockType.PRODUCT_CARD;
  }

  public void feature() {
    this.profileHighlighted = true;
  }

  public void unfeature() {
    this.profileHighlighted = false;
  }
}
