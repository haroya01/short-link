package com.example.short_link.link.og.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 1:1 with link.id — split out of LinkEntity to keep OG/social fields off the hot path. */
@Entity
@Table(name = "link_og_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkOgMetadataEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(name = "og_title", length = 300)
  private String ogTitle;

  @Column(name = "og_description", length = 800)
  private String ogDescription;

  @Column(name = "og_image", length = 1024)
  private String ogImage;

  @Column(name = "og_fetched_at")
  private Instant ogFetchedAt;

  @Column(name = "og_fetch_status", nullable = false, length = 20)
  private String ogFetchStatus = "PENDING";

  @Column(name = "og_fetch_attempts", nullable = false)
  private int ogFetchAttempts = 0;

  @Column(name = "og_title_override", length = 300)
  private String ogTitleOverride;

  @Column(name = "og_description_override", length = 800)
  private String ogDescriptionOverride;

  @Column(name = "og_image_override", length = 1024)
  private String ogImageOverride;

  public LinkOgMetadataEntity(LinkId linkId) {
    this.linkId = linkId == null ? null : linkId.value();
  }

  public void applyFetched(String title, String description, String image, Instant fetchedAt) {
    this.ogTitle = title;
    this.ogDescription = description;
    this.ogImage = image;
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = "OK";
    this.ogFetchAttempts++;
  }

  public void markFetchFailed(Instant fetchedAt, boolean willRetry) {
    this.ogFetchedAt = fetchedAt;
    this.ogFetchStatus = willRetry ? "RETRYABLE" : "ERROR";
    this.ogFetchAttempts++;
  }

  public void changeOverride(String title, String description, String image) {
    this.ogTitleOverride = blankToNull(title);
    this.ogDescriptionOverride = blankToNull(description);
    this.ogImageOverride = blankToNull(image);
  }

  public void resetForNewUrl() {
    this.ogTitle = null;
    this.ogDescription = null;
    this.ogImage = null;
    this.ogFetchedAt = null;
    this.ogFetchStatus = "PENDING";
  }

  public String effectiveTitle() {
    return notBlank(ogTitleOverride) ? ogTitleOverride : ogTitle;
  }

  public String effectiveDescription() {
    return notBlank(ogDescriptionOverride) ? ogDescriptionOverride : ogDescription;
  }

  public String effectiveImage() {
    return notBlank(ogImageOverride) ? ogImageOverride : ogImage;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
