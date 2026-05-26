package com.example.short_link.campaign.domain;

import com.example.short_link.campaign.domain.repository.*;
import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.link.domain.LinkId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampaignBatchEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  /** 이 batch 가 대표하는 단축 링크. Batch:Link = 1:1 (DB 에서 UNIQUE). */
  @Column(name = "link_id", nullable = false)
  private Long linkId;

  public LinkId linkId() {
    return linkId == null ? null : new LinkId(linkId);
  }

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "distributor_name", length = 255)
  private String distributorName;

  @Column(name = "area_label", length = 255)
  private String areaLabel;

  /** 인쇄/배포 수량 메타데이터. Link 개수와는 무관 (Batch:인쇄물 = 1:N). */
  @Column(nullable = false)
  private int quantity;

  @Column(length = 500)
  private String memo;

  public CampaignBatchEntity(
      Long campaignId,
      LinkId linkId,
      String name,
      String distributorName,
      String areaLabel,
      int quantity,
      String memo) {
    this.campaignId = campaignId;
    this.linkId = linkId == null ? null : linkId.value();
    this.name = name;
    this.distributorName = distributorName;
    this.areaLabel = areaLabel;
    this.quantity = quantity;
    this.memo = memo;
  }

  /** Metadata 만 수정 — 대표 link 와의 결합은 immutable (인쇄된 QR 의 destination 안전). */
  public void editMetadata(
      String name, String distributorName, String areaLabel, int quantity, String memo) {
    this.name = name;
    this.distributorName = distributorName;
    this.areaLabel = areaLabel;
    this.quantity = quantity;
    this.memo = memo;
  }
}
