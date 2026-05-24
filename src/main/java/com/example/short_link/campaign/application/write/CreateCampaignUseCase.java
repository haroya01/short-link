package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.application.dto.CampaignCreateRequest;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCampaignUseCase {

  private final CampaignRepository repository;

  @Transactional
  public CampaignEntity execute(Long ownerId, CampaignCreateRequest request) {
    Instant now = Instant.now();
    Instant startsAt = request.startsAt() != null ? request.startsAt() : now;
    if (!request.endsAt().isAfter(startsAt)) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD);
    }
    CampaignPostEndAction action =
        request.postEndAction() != null ? request.postEndAction() : CampaignPostEndAction.KEEP;
    if (action == CampaignPostEndAction.REDIRECT && isBlank(request.postEndDestinationUrl())) {
      throw new CampaignException(CampaignErrorCode.MISSING_POST_END_DESTINATION);
    }
    CampaignEntity campaign =
        new CampaignEntity(
            ownerId,
            request.name(),
            startsAt,
            request.endsAt(),
            request.defaultDestinationUrl(),
            action,
            request.postEndDestinationUrl(),
            request.postEndMessage());
    campaign.activateIfStarted(now);
    return repository.save(campaign);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
