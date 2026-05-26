package com.example.short_link.link.profilebinding.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1:1 with link.id — split out of LinkEntity for the profile-feed binding fields. */
@Entity
@Table(name = "link_profile_binding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkProfileBindingEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "profile_order")
  private Integer profileOrder;

  @Column(name = "profile_highlighted", nullable = false)
  private boolean profileHighlighted = false;

  public LinkProfileBindingEntity(Long linkId) {
    this.linkId = linkId;
  }

  public void changeProfileOrder(Integer order) {
    this.profileOrder = order;
  }

  public void changeProfileHighlighted(boolean highlighted) {
    this.profileHighlighted = highlighted;
  }
}
