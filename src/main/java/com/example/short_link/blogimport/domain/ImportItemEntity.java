package com.example.short_link.blogimport.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blog_import_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportItemEntity extends BaseCreatedEntity {

  public static final int MAX_ATTEMPTS = 3;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "source_id", nullable = false)
  private Long sourceId;

  @Column(name = "url_hash", nullable = false, length = 64)
  private String urlHash;

  @Column(name = "post_id")
  private Long postId;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "imported_at")
  private Instant importedAt;

  public ImportItemEntity(Long sourceId, String urlHash) {
    this.sourceId = sourceId;
    this.urlHash = urlHash;
  }

  public boolean isSettled() {
    return importedAt != null || attempts >= MAX_ATTEMPTS;
  }

  public void attachPost(Long postId) {
    this.postId = postId;
  }

  public void fail() {
    this.attempts++;
  }

  public void complete(Instant at) {
    this.importedAt = at;
  }
}
