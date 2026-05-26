package com.example.short_link.campaign.application.write;

import java.util.List;

public record CampaignBatchBulkCommand(List<CampaignBatchCreateCommand> batches) {}
