package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignPostEndAction;
import java.time.Instant;

public record CreateCampaignCommand(
    Long ownerId,
    String name,
    Instant startsAt,
    Instant endsAt,
    String defaultDestinationUrl,
    CampaignPostEndAction postEndAction,
    String postEndDestinationUrl,
    String postEndMessage) {}
