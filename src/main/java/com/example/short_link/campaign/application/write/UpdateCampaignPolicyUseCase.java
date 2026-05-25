package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.application.dto.CampaignUpdateRequest;
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
  public CampaignEntity execute(Long id, Long ownerId, CampaignUpdateRequest request) {
    CampaignEntity c = ownership.require(id, ownerId);
    if (c.getStatus() == CampaignStatus.ARCHIVED) {
      throw new CampaignException(CampaignErrorCode.CAMPAIGN_ARCHIVED);
    }
    Instant endsAt = request.endsAt() != null ? request.endsAt() : c.getEndsAt();
    if (!endsAt.isAfter(c.getStartsAt())) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD);
    }
    CampaignPostEndAction action =
        request.postEndAction() != null ? request.postEndAction() : c.getPostEndAction();
    String postEndUrl =
        request.postEndDestinationUrl() != null
            ? request.postEndDestinationUrl()
            : c.getPostEndDestinationUrl();
    if (action == CampaignPostEndAction.REDIRECT && isBlank(postEndUrl)) {
      throw new CampaignException(CampaignErrorCode.MISSING_POST_END_DESTINATION);
    }
    String defaultDest =
        request.defaultDestinationUrl() != null
            ? request.defaultDestinationUrl()
            : c.getDefaultDestinationUrl();
    String postEndMessage =
        request.postEndMessage() != null ? request.postEndMessage() : c.getPostEndMessage();
    if (request.name() != null) {
      c.rename(request.name());
    }
    c.updatePolicy(endsAt, defaultDest, action, postEndUrl, postEndMessage);
    return c;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
