package com.example.short_link.campaign.application.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.campaign")
public record CampaignProperties(boolean lifecycleEnabled) {}
