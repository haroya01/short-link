package com.example.short_link.campaign.presentation.request;

import com.example.short_link.campaign.application.write.CreateCampaignCommand;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;

/** {@code startsAt} 생략 시 즉시 시작한다. REDIRECT 정책은 종료 후 목적지 URL이 필수다. */
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
