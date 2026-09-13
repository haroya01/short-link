package com.example.short_link.post.collection.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
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
@Table(name = "collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionEntity extends BaseTimeEntity {

  public static final int MAX_TITLE = 120;
  public static final int MAX_DESCRIPTION = 280;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(nullable = false, length = MAX_TITLE)
  private String title;

  @Column(length = MAX_DESCRIPTION)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CollectionVisibility visibility;

  /** 생성 시 고정하며 이후 변경하지 않는다. */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private CollectionKind kind;

  public CollectionEntity(
      Long ownerId,
      String title,
      String description,
      CollectionVisibility visibility,
      CollectionKind kind) {
    this.ownerId = ownerId;
    edit(title, description, visibility);
    this.kind = kind == null ? CollectionKind.COLLECTION : kind;
  }

  public boolean isOwnedBy(Long viewerId) {
    return ownerId.equals(viewerId);
  }

  public boolean isVisibleTo(Long viewerId) {
    return isOwnedBy(viewerId) || visibility.isVisibleToOthers();
  }

  public void edit(String title, String description, CollectionVisibility visibility) {
    this.title = normalizeTitle(title);
    this.description = normalizeDescription(description);
    this.visibility = visibility;
  }

  private static String normalizeTitle(String raw) {
    String title = raw == null ? "" : raw.strip();
    if (title.isEmpty()) {
      throw new PostException(PostErrorCode.COLLECTION_TITLE_REQUIRED);
    }
    return title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
  }

  private static String normalizeDescription(String raw) {
    if (raw == null) return null;
    String description = raw.strip();
    if (description.isEmpty()) return null;
    return description.length() > MAX_DESCRIPTION
        ? description.substring(0, MAX_DESCRIPTION)
        : description;
  }
}
