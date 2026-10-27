package com.example.short_link.link.expiration.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_expiration_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkExpirationPolicyEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(name = "blocked_countries", length = 255)
  private String blockedCountries;

  @Column(name = "expired_message", length = 500)
  private String expiredMessage;

  @Column(name = "expired_redirect_url", length = 2048)
  private String expiredRedirectUrl;

  public LinkExpirationPolicyEntity(LinkId linkId) {
    this.linkId = linkId == null ? null : linkId.value();
  }

  public void mirrorBlockedCountries(LinkEntity link) {
    this.blockedCountries = link.getBlockedCountries();
  }

  public void mirrorExpiredMessage(LinkEntity link) {
    this.expiredMessage = link.getExpiredMessage();
  }

  public void mirrorExpiredRedirectUrl(LinkEntity link) {
    this.expiredRedirectUrl = link.getExpiredRedirectUrl();
  }
}
