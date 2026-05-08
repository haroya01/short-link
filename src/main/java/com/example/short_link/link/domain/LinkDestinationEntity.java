package com.example.short_link.link.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One A/B variant for a link. When a link has any rows here, the redirect handler picks among
 * enabled variants by weight; otherwise it falls back to {@code link.original_url}. The original
 * URL is implicitly the "control" — owners can promote any variant by either bumping its weight or
 * replacing the original.
 */
@Entity
@Table(name = "link_destination")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkDestinationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(nullable = false)
  private int weight = 1;

  @Column(length = 40)
  private String label;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public LinkDestinationEntity(Long linkId, String url, int weight, String label) {
    this.linkId = linkId;
    this.url = url;
    this.weight = Math.max(1, weight);
    this.label = label;
    this.enabled = true;
  }

  @PrePersist
  void prePersist() {
    this.createdAt = Instant.now();
  }

  public void update(String url, Integer weight, String label, Boolean enabled) {
    if (url != null) this.url = url;
    if (weight != null) this.weight = Math.max(1, weight);
    if (label != null) this.label = label;
    if (enabled != null) this.enabled = enabled;
  }
}
