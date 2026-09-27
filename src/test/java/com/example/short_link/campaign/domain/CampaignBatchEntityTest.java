package com.example.short_link.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.link.domain.LinkId;
import org.junit.jupiter.api.Test;

class CampaignBatchEntityTest {

  @Test
  void editMetadataUpdatesAllowedFields() {
    CampaignBatchEntity batch =
        new CampaignBatchEntity(10L, new LinkId(100L), "Old", "A", "East", 500, "first batch");

    batch.editMetadata("New", "B", "West", 700, "second drop");

    assertThat(batch.getName()).isEqualTo("New");
    assertThat(batch.getDistributorName()).isEqualTo("B");
    assertThat(batch.getAreaLabel()).isEqualTo("West");
    assertThat(batch.getQuantity()).isEqualTo(700);
    assertThat(batch.getMemo()).isEqualTo("second drop");
  }

  @Test
  void editMetadataDoesNotTouchAssociations() {
    CampaignBatchEntity batch =
        new CampaignBatchEntity(10L, new LinkId(100L), "Name", "A", "East", 500, null);

    batch.editMetadata("Renamed", "C", "North", 200, "memo");

    assertThat(batch.getCampaignId()).isEqualTo(10L);
    assertThat(batch.getLinkId()).isEqualTo(100L);
  }

  @Test
  void constructorRejectsInvalidMetadataWithoutApplicationValidation() {
    assertThatThrownBy(
            () -> new CampaignBatchEntity(10L, new LinkId(100L), " ", null, null, 1, null))
        .isInstanceOf(CampaignException.class);
    assertThatThrownBy(
            () -> new CampaignBatchEntity(10L, new LinkId(100L), "Name", null, null, 0, null))
        .isInstanceOf(CampaignException.class);
  }

  @Test
  void rejectedMetadataEditPreservesAllExistingValues() {
    CampaignBatchEntity batch =
        new CampaignBatchEntity(10L, new LinkId(100L), "Old", "A", "East", 500, "first batch");

    assertThatThrownBy(() -> batch.editMetadata("New", "B", "West", 0, "Changed"))
        .isInstanceOfSatisfying(
            CampaignException.class,
            e -> assertThat(e.errorCode()).isEqualTo(CampaignErrorCode.INVALID_BATCH_METADATA));

    assertThat(batch.getName()).isEqualTo("Old");
    assertThat(batch.getDistributorName()).isEqualTo("A");
    assertThat(batch.getAreaLabel()).isEqualTo("East");
    assertThat(batch.getQuantity()).isEqualTo(500);
    assertThat(batch.getMemo()).isEqualTo("first batch");
  }

  @Test
  void metadataLengthLimitIsValidatedBeforeMutation() {
    CampaignBatchEntity batch =
        new CampaignBatchEntity(10L, new LinkId(100L), "Name", null, null, 1, "m".repeat(500));

    assertThatThrownBy(() -> batch.editMetadata("Changed", null, null, 2, "m".repeat(501)))
        .isInstanceOf(CampaignException.class);
    assertThat(batch.getName()).isEqualTo("Name");
    assertThat(batch.getQuantity()).isEqualTo(1);
    assertThat(batch.getMemo()).hasSize(500);
  }
}
