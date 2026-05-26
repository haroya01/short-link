package com.example.short_link.campaign.application.write;

public record CampaignBatchUpdateCommand(
    String name, String distributorName, String areaLabel, Integer quantity, String memo) {}
