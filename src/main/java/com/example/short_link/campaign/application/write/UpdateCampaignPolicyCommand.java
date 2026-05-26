package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignPostEndAction;
import java.time.Instant;

public record UpdateCampaignPolicyCommand(
    Long campaignId,
    Long ownerId,
    String name,
    Instant endsAt,
    String defaultDestinationUrl,
    CampaignPostEndAction postEndAction,
    String postEndDestinationUrl,
    String postEndMessage) {}
