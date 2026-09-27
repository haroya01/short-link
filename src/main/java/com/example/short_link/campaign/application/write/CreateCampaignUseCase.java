package com.example.short_link.campaign.application.write;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.repository.CampaignRepository;
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
    CampaignEntity campaign =
        new CampaignEntity(
            command.ownerId(),
            command.name(),
            startsAt,
            command.endsAt(),
            command.defaultDestinationUrl(),
            command.postEndAction(),
            command.postEndDestinationUrl(),
            command.postEndMessage());
    campaign.activateIfStarted(now);
    return repository.save(campaign);
  }
}
