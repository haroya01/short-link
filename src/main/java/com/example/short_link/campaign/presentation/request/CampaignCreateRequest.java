package com.example.short_link.campaign.presentation.request;

import com.example.short_link.campaign.application.write.CreateCampaignCommand;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

/**
 * startsAt 이 null 이면 "지금 시작" 으로 해석되어 service 가 now 로 박는다. endsAt 은 필수 — 무기한 캠페인은 도구 정체성을 흐려서 막는다.
 * postEndAction = REDIRECT 일 때 postEndDestinationUrl 은 service 가 필수 검증한다 (cross-field 라 record 단위
 * validation 에서 빠짐).
 */
public record CampaignCreateRequest(
    @NotBlank @Size(max = 255) String name,
    Instant startsAt,
    @NotNull Instant endsAt,
    @URL
        @Pattern(regexp = "^(https?://.*)?$", message = "URL must use http or https")
        @Size(max = 2048)
        String defaultDestinationUrl,
    CampaignPostEndAction postEndAction,
    @URL
        @Pattern(regexp = "^(https?://.*)?$", message = "URL must use http or https")
        @Size(max = 2048)
        String postEndDestinationUrl,
    @Size(max = 500) String postEndMessage) {

  public CreateCampaignCommand toCommand(Long ownerId) {
    return new CreateCampaignCommand(
        ownerId,
        name,
        startsAt,
        endsAt,
        defaultDestinationUrl,
        postEndAction,
        postEndDestinationUrl,
        postEndMessage);
  }
}
