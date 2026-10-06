package com.example.short_link.note.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "note_link_preview")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoteLinkPreviewEntity {

  @Id
  @Column(name = "note_id")
  private Long noteId;

  @Column(name = "url", nullable = false, length = 2048)
  private String url;

  @Column(name = "title", length = 300)
  private String title;

  @Column(name = "description", length = 800)
  private String description;

  @Column(name = "image_url", length = 1024)
  private String imageUrl;

  @Column(name = "fetched_at", nullable = false)
  private Instant fetchedAt;
}
