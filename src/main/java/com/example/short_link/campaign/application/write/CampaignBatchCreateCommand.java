package com.example.short_link.campaign.application.write;

public record CampaignBatchCreateCommand(
    String name,
    String distributorName,
    String areaLabel,
    int quantity,
    String destinationUrl,
    String memo) {}
