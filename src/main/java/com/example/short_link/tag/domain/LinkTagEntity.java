package com.example.short_link.tag.domain;

import com.example.short_link.link.domain.repository.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_tag")
@IdClass(LinkTagEntity.LinkTagId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkTagEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Id
  @Column(name = "tag_id")
  private Long tagId;

  public LinkTagEntity(Long linkId, Long tagId) {
    this.linkId = linkId;
    this.tagId = tagId;
  }

  public static class LinkTagId implements Serializable {
    private Long linkId;
    private Long tagId;

    public LinkTagId() {}

    public LinkTagId(Long linkId, Long tagId) {
      this.linkId = linkId;
      this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LinkTagId other)) return false;
      return Objects.equals(linkId, other.linkId) && Objects.equals(tagId, other.tagId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(linkId, tagId);
    }
  }
}
