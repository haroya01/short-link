package com.example.short_link.cta.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reusable Call-to-Action library entity. 작성자가 라이브러리에서 생성/관리하고 글의 CTA_REF 블록에서 참조. 소프트 삭제 (분석 데이터
 * 보존, 과거 발행 글의 참조 무결성).
 */
@Entity
@Table(name = "cta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CtaEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 100)
  private String label;

  @Column(nullable = false, length = 2048)
  private String url;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CtaStyle style = CtaStyle.PRIMARY;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CtaPurpose purpose = CtaPurpose.CUSTOM;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  /**
   * Short code of the kurl link this CTA's target resolves to (wrapped at save time, or the code of
   * an already-kurl URL). The public post serves this short link so clicks are measured +
   * attributed. Null when tracking couldn't be established (e.g. the user's link quota is full).
   */
  @Column(name = "tracked_short_code", length = 16)
  private String trackedShortCode;

  public CtaEntity(Long userId, String label, String url, CtaStyle style, CtaPurpose purpose) {
    this.userId = userId;
    this.label = label;
    this.url = url;
    this.style = style;
    this.purpose = purpose;
  }

  /** Set/replace the tracking short code (the kurl link a click on this CTA flows through). */
  public void trackVia(String shortCode) {
    this.trackedShortCode = shortCode;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void updateLabel(String label) {
    requireAlive();
    this.label = label;
  }

  public void updateUrl(String url) {
    requireAlive();
    this.url = url;
  }

  public void updateStyle(CtaStyle style) {
    requireAlive();
    this.style = style;
  }

  public void updatePurpose(CtaPurpose purpose) {
    requireAlive();
    this.purpose = purpose;
  }

  public void softDelete() {
    if (deletedAt != null) return;
    this.deletedAt = Instant.now();
  }

  private void requireAlive() {
    if (deletedAt != null) {
      throw new CtaException(CtaErrorCode.CTA_DELETED, id);
    }
  }
}
