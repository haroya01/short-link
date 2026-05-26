package com.example.short_link.campaign.presentation.request;

import com.example.short_link.campaign.application.write.CampaignBatchBulkCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CampaignBatchBulkRequest(
    @NotEmpty @Size(max = 500) @Valid List<CampaignBatchCreateRequest> batches) {

  public CampaignBatchBulkCommand toCommand() {
    return new CampaignBatchBulkCommand(
        batches.stream().map(CampaignBatchCreateRequest::toCommand).toList());
  }
}
