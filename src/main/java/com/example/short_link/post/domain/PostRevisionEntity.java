package com.example.short_link.post.domain;

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

/**
 * 발행 시점 스냅샷. (b) revision history — diff 뷰는 v0 X, 이전 발행 버전 복원만. titleSnapshot 은 revision list 표시 시
 * contentJson 파싱 안 하려고 따로 둠.
 */
@Entity
@Table(
    name = "post_revision",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_post_revision_post_version",
            columnNames = {"post_id", "version_number"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRevisionEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "version_number", nullable = false)
  private Integer versionNumber;

  @Column(name = "title_snapshot", nullable = false, length = 200)
  private String titleSnapshot;

  @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT")
  private String contentJson;

  public PostRevisionEntity(
      Long postId, int versionNumber, String titleSnapshot, String contentJson) {
    this.postId = postId;
    this.versionNumber = versionNumber;
    this.titleSnapshot = titleSnapshot;
    this.contentJson = contentJson;
  }

  public boolean belongsTo(Long postId) {
    return this.postId.equals(postId);
  }
}
