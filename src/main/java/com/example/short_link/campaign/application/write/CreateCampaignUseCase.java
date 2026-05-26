package com.example.short_link.campaign.application.write;

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
  public CampaignEntity execute(CreateCampaignCommand command) {
    Instant now = Instant.now();
    Instant startsAt = command.startsAt() != null ? command.startsAt() : now;
    if (!command.endsAt().isAfter(startsAt)) {
      throw new CampaignException(CampaignErrorCode.INVALID_CAMPAIGN_PERIOD);
    }
    CampaignPostEndAction action =
        command.postEndAction() != null ? command.postEndAction() : CampaignPostEndAction.KEEP;
    if (action == CampaignPostEndAction.REDIRECT && isBlank(command.postEndDestinationUrl())) {
      throw new CampaignException(CampaignErrorCode.MISSING_POST_END_DESTINATION);
    }
    CampaignEntity campaign =
        new CampaignEntity(
            command.ownerId(),
            command.name(),
            startsAt,
            command.endsAt(),
            command.defaultDestinationUrl(),
            action,
            command.postEndDestinationUrl(),
            command.postEndMessage());
    campaign.activateIfStarted(now);
    return repository.save(campaign);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
