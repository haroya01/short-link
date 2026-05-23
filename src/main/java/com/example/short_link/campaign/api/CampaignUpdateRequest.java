package com.example.short_link.campaign.api;

import com.example.short_link.campaign.domain.CampaignPostEndAction;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

/** 모든 필드 nullable — 부분 수정 (null 은 기존 값 유지). startsAt 은 의도적으로 수정 불가. */
public record CampaignUpdateRequest(
    @Size(max = 255) String name,
    Instant endsAt,
    @URL @Size(max = 2048) String defaultDestinationUrl,
    CampaignPostEndAction postEndAction,
    @URL @Size(max = 2048) String postEndDestinationUrl,
    @Size(max = 500) String postEndMessage) {}
