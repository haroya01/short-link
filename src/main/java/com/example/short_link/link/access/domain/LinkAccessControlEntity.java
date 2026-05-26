package com.example.short_link.link.access.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 with link.id — split out of LinkEntity for the access-policy fields (password + view cap).
 */
@Entity
@Table(name = "link_access_control")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkAccessControlEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(name = "password_hash", length = 60)
  private String passwordHash;

  @Column(name = "max_views")
  private Integer maxViews;

  public LinkAccessControlEntity(LinkId linkId) {
    this.linkId = linkId == null ? null : linkId.value();
  }

  public void changePasswordHash(String hash) {
    this.passwordHash = (hash == null || hash.isBlank()) ? null : hash;
  }

  public void changeMaxViews(Integer max) {
    this.maxViews = max;
  }

  public boolean hasPassword() {
    return passwordHash != null && !passwordHash.isEmpty();
  }
}
