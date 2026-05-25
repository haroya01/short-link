package com.example.short_link.link.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1:1 with link.id — split out of LinkEntity for the post-expiration policy fields. */
@Entity
@Table(name = "link_expiration_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkExpirationPolicyEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "blocked_countries", length = 255)
  private String blockedCountries;

  @Column(name = "expired_message", length = 500)
  private String expiredMessage;

  @Column(name = "expired_redirect_url", length = 2048)
  private String expiredRedirectUrl;

  public LinkExpirationPolicyEntity(Long linkId) {
    this.linkId = linkId;
  }

  public void changeBlockedCountries(String csv) {
    this.blockedCountries = (csv == null || csv.isBlank()) ? null : csv;
  }

  public void changeExpiredMessage(String message) {
    this.expiredMessage = truncate(message, 500);
  }

  public void changeExpiredRedirectUrl(String url) {
    this.expiredRedirectUrl = (url == null || url.isBlank()) ? null : url.trim();
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    String trimmed = s.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
