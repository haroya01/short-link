package com.example.short_link.link.visit.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "link_visit_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkVisitOptionEntity extends BaseTimeEntity {

  @Id
  @Column(name = "link_id")
  private Long linkId;

  @Column(name = "open_in_browser", nullable = false)
  private boolean openInBrowser;

  public LinkVisitOptionEntity(LinkId linkId) {
    this.linkId = linkId == null ? null : linkId.value();
  }

  public void changeOpenInBrowser(boolean openInBrowser) {
    this.openInBrowser = openInBrowser;
  }
}
