package com.example.short_link.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampaignBatchEntityTest {

  @Test
  void editMetadataUpdatesAllowedFields() {
    CampaignBatchEntity batch =
        new CampaignBatchEntity(10L, 100L, "Old", "A", "East", 500, "first batch");

    batch.editMetadata("New", "B", "West", 700, "second drop");

    assertThat(batch.getName()).isEqualTo("New");
    assertThat(batch.getDistributorName()).isEqualTo("B");
    assertThat(batch.getAreaLabel()).isEqualTo("West");
    assertThat(batch.getQuantity()).isEqualTo(700);
    assertThat(batch.getMemo()).isEqualTo("second drop");
  }

  @Test
  void editMetadataDoesNotTouchAssociations() {
    CampaignBatchEntity batch = new CampaignBatchEntity(10L, 100L, "Name", "A", "East", 500, null);

    batch.editMetadata("Renamed", "C", "North", 200, "memo");

    assertThat(batch.getCampaignId()).isEqualTo(10L);
    assertThat(batch.getLinkId()).isEqualTo(100L);
  }
}
