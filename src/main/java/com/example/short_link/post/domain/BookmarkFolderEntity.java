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
 * A user-made folder over their bookmarks ("스마트 셸프"). A bookmark with {@code folder_id = NULL} is
 * unfiled (auto-grouped by tag in the UI); filing it points it at one of these. Folder names are
 * unique per user.
 */
@Entity
@Table(
    name = "bookmark_folder",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_bookmark_folder_user_name",
            columnNames = {"user_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookmarkFolderEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private String name;

  public BookmarkFolderEntity(Long userId, String name) {
    this.userId = userId;
    this.name = name;
  }

  public void rename(String name) {
    this.name = name;
  }
}
