package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateCampaignPolicyUseCase {

  private final CampaignOwnership ownership;

  @Transactional
  public CampaignEntity execute(UpdateCampaignPolicyCommand command) {
    CampaignEntity c = ownership.require(command.campaignId(), command.ownerId());
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_ARCHIVED);
    }
    Instant endsAt = command.endsAt() != null ? command.endsAt() : c.getEndsAt();
    if (!endsAt.isAfter(c.getStartsAt())) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD);
    }
    CampaignPostEndAction action =
        command.postEndAction() != null ? command.postEndAction() : c.getPostEndAction();
    String postEndUrl =
        command.postEndDestinationUrl() != null
            ? command.postEndDestinationUrl()
            : c.getPostEndDestinationUrl();
    if (action == CampaignPostEndAction.REDIRECT && isBlank(postEndUrl)) {
      throw new CampaignException(CampaignErrorCode.MISSING_POST_END_DESTINATION);
    }
    String defaultDest =
        command.defaultDestinationUrl() != null
            ? command.defaultDestinationUrl()
            : c.getDefaultDestinationUrl();
    String postEndMessage =
        command.postEndMessage() != null ? command.postEndMessage() : c.getPostEndMessage();
    if (command.name() != null) {
      c.rename(command.name());
    }
    c.updatePolicy(endsAt, defaultDest, action, postEndUrl, postEndMessage);
    return c;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
