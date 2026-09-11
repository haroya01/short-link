package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
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
    String name = command.name() != null ? command.name() : c.getName();
    Instant endsAt = command.endsAt() != null ? command.endsAt() : c.getEndsAt();
    CampaignPostEndAction action =
        command.postEndAction() != null ? command.postEndAction() : c.getPostEndAction();
    String postEndUrl =
        command.postEndDestinationUrl() != null
            ? command.postEndDestinationUrl()
            : c.getPostEndDestinationUrl();
    String defaultDest =
        command.defaultDestinationUrl() != null
            ? command.defaultDestinationUrl()
            : c.getDefaultDestinationUrl();
    String postEndMessage =
        command.postEndMessage() != null ? command.postEndMessage() : c.getPostEndMessage();
    c.updateDetails(name, endsAt, defaultDest, action, postEndUrl, postEndMessage);
    return c;
  }
}
