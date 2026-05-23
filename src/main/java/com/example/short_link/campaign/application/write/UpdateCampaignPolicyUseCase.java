package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.application.CampaignArchivedException;
import com.example.short_link.campaign.application.CampaignUpdateRequest;
import com.example.short_link.campaign.application.InvalidCampaignPeriodException;
import com.example.short_link.campaign.application.MissingPostEndDestinationException;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
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
      throw new CampaignArchivedException();
    }
    Instant endsAt = request.endsAt() != null ? request.endsAt() : c.getEndsAt();
    if (!endsAt.isAfter(c.getStartsAt())) {
      throw new InvalidCampaignPeriodException();
    }
    CampaignPostEndAction action =
        request.postEndAction() != null ? request.postEndAction() : c.getPostEndAction();
    String postEndUrl =
        request.postEndDestinationUrl() != null
            ? request.postEndDestinationUrl()
            : c.getPostEndDestinationUrl();
    if (action == CampaignPostEndAction.REDIRECT && isBlank(postEndUrl)) {
      throw new MissingPostEndDestinationException();
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
