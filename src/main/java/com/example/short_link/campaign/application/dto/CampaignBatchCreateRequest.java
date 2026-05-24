package com.example.short_link.campaign.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CampaignBatchCreateRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 255) String distributorName,
    @Size(max = 255) String areaLabel,
    @Positive int quantity,
    @URL @Size(max = 2048) String destinationUrl,
    @Size(max = 500) String memo) {}
