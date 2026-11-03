package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.campaign.application.read.CampaignQueryService;
import com.example.short_link.campaign.application.write.CampaignBatchUpdateCommand;
import com.example.short_link.campaign.domain.CampaignBatchEntity;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.repository.CampaignBatchRepository;
import com.example.short_link.link.application.CampaignLinks;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.application.read.LinkRefReader;
import com.example.short_link.link.application.write.CreateLinkUseCase;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class CampaignBatchMetadataUpdateTest {

  private final CampaignBatchRepository batches = mock(CampaignBatchRepository.class);
  private final CampaignQueryService campaigns = mock(CampaignQueryService.class);
  private final LinkRefReader links = mock(LinkRefReader.class);
  private final CampaignBatchService service =
      new CampaignBatchService(
          batches,
          mock(CreateLinkUseCase.class),
          campaigns,
          links,
          mock(CampaignLinks.class),
          mock(TransactionTemplate.class));
  private final CampaignBatchEntity batch =
      new CampaignBatchEntity(1L, new LinkId(9L), "Station", "Team", "Tokyo", 100, "Entrance A");

  CampaignBatchMetadataUpdateTest() {
    Instant start = Instant.parse("2026-05-22T00:00:00Z");
    CampaignEntity campaign =
        new CampaignEntity(
            7L,
            "Autumn",
            start,
            start.plusSeconds(3600),
            null,
            CampaignPostEndAction.KEEP,
            null,
            null);
    ReflectionTestUtils.setField(batch, "id", 3L);
    when(campaigns.detail(1L, 7L)).thenReturn(campaign);
    when(batches.findById(3L)).thenReturn(Optional.of(batch));
    when(links.find(new LinkId(9L)))
        .thenReturn(
            Optional.of(
                new LinkRef(new LinkId(9L), 7L, ShortCode.of("abc123"), "https://a.example/")));
  }

  @Test
  void anEmptyStringClearsDistributorAreaAndMemoWhileNullKeepsThem() {
    service.update(1L, 3L, 7L, new CampaignBatchUpdateCommand(null, "", null, null, ""));

    assertThat(batch.getName()).isEqualTo("Station");
    assertThat(batch.getDistributorName()).isNull();
    assertThat(batch.getAreaLabel()).isEqualTo("Tokyo");
    assertThat(batch.getQuantity()).isEqualTo(100);
    assertThat(batch.getMemo()).isNull();
  }

  @Test
  void anEmptyNameKeepsTheNameBecauseABatchAlwaysHasOne() {
    service.update(1L, 3L, 7L, new CampaignBatchUpdateCommand("", null, "", null, null));

    assertThat(batch.getName()).isEqualTo("Station");
    assertThat(batch.getDistributorName()).isEqualTo("Team");
    assertThat(batch.getAreaLabel()).isNull();
    assertThat(batch.getMemo()).isEqualTo("Entrance A");
  }
}
