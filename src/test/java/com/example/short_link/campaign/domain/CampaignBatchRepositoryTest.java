package com.example.short_link.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.domain.repository.*;
import com.example.short_link.campaign.infrastructure.persistence.JpaCampaignBatchRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignBatchRepositoryTest {

  @Autowired private JpaCampaignBatchRepository batchRepository;
  @Autowired private CampaignRepository campaignRepository;
  @Autowired private LinkRepository linkRepository;

  private CampaignEntity newCampaign() {
    Instant now = Instant.parse("2026-05-22T01:00:00Z");
    return campaignRepository.save(
        new CampaignEntity(
            42L, "C", now, now.plusSeconds(3600), null, CampaignPostEndAction.KEEP, null, null));
  }

  private LinkEntity newLink(String code) {
    return linkRepository.save(new LinkEntity("https://example.com", code));
  }

  @Test
  void listsBatchesByCampaignInInsertionOrder() {
    CampaignEntity campaign = newCampaign();
    CampaignBatchEntity first =
        batchRepository.save(
            new CampaignBatchEntity(
                campaign.getId(), newLink("ba0001").linkId(), "first", "A", "East", 100, null));
    CampaignBatchEntity second =
        batchRepository.save(
            new CampaignBatchEntity(
                campaign.getId(), newLink("ba0002").linkId(), "second", "B", "West", 200, null));

    List<CampaignBatchEntity> found =
        batchRepository.findByCampaignIdOrderByCreatedAtAsc(campaign.getId());

    assertThat(found)
        .extracting(CampaignBatchEntity::getId)
        .containsExactly(first.getId(), second.getId());
  }

  @Test
  void countsBatchesPerCampaign() {
    CampaignEntity campaign = newCampaign();
    batchRepository.save(
        new CampaignBatchEntity(
            campaign.getId(), newLink("cnt001").linkId(), "n1", null, null, 50, null));
    batchRepository.save(
        new CampaignBatchEntity(
            campaign.getId(), newLink("cnt002").linkId(), "n2", null, null, 50, null));

    assertThat(batchRepository.countByCampaignId(campaign.getId())).isEqualTo(2L);
  }

  @Test
  void rejectsDuplicateLinkAcrossBatches() {
    CampaignEntity campaign = newCampaign();
    LinkEntity link = newLink("dup001");
    batchRepository.save(
        new CampaignBatchEntity(campaign.getId(), link.linkId(), "a", null, null, 10, null));

    org.junit.jupiter.api.Assertions.assertThrows(
        DataIntegrityViolationException.class,
        () ->
            batchRepository.saveAndFlush(
                new CampaignBatchEntity(
                    campaign.getId(), link.linkId(), "b", null, null, 10, null)));
  }
}
