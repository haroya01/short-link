package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.campaign.presentation.request.CampaignBatchBulkRequest;
import com.example.short_link.campaign.presentation.request.CampaignBatchCreateRequest;
import com.example.short_link.campaign.presentation.request.CampaignCreateRequest;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignBatchServiceTest {

  @Autowired private CampaignBatchService batchService;

  @Autowired
  private com.example.short_link.campaign.application.write.CreateCampaignUseCase createCampaign;

  @Autowired
  private com.example.short_link.campaign.application.write.ArchiveCampaignUseCase archiveCampaign;

  @Autowired private UserRepository userRepository;

  private Long newOwner(String suffix) {
    UserEntity user =
        userRepository.save(new UserEntity("u-batch-" + suffix + "@x.com", "google", suffix));
    return user.getId();
  }

  private CampaignEntity newCampaign(Long ownerId, String defaultDestination) {
    return createCampaign.execute(
        new CampaignCreateRequest(
                "C", null, Instant.now().plusSeconds(3600), defaultDestination, null, null, null)
            .toCommand(ownerId));
  }

  @Test
  void createsBatchUsingCampaignDefaultDestination() {
    Long owner = newOwner("def");
    CampaignEntity campaign = newCampaign(owner, "https://example.com/landing");

    BatchWithLink result =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", "A", "Shinjuku East", 500, null, null)
                .toCommand());

    assertThat(result.batch().getLinkId()).isEqualTo(result.link().getId());
    assertThat(result.link().getOriginalUrl()).isEqualTo("https://example.com/landing");
    assertThat(result.batch().getQuantity()).isEqualTo(500);
  }

  @Test
  void perRowDestinationOverridesCampaignDefault() {
    Long owner = newOwner("override");
    CampaignEntity campaign = newCampaign(owner, "https://example.com/default");

    BatchWithLink result =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest(
                    "south", null, null, 100, "https://example.com/special", null)
                .toCommand());

    assertThat(result.link().getOriginalUrl()).isEqualTo("https://example.com/special");
  }

  @Test
  void rejectsCreateWhenNoDestinationResolvable() {
    Long owner = newOwner("nodest");
    CampaignEntity campaign = newCampaign(owner, null);

    assertThatThrownBy(
            () ->
                batchService.create(
                    campaign.getId(),
                    owner,
                    new CampaignBatchCreateRequest("n", null, null, 50, null, null).toCommand()))
        .isInstanceOf(CampaignException.class);
  }

  @Test
  void rejectsBatchCreateOnArchivedCampaign() {
    Long owner = newOwner("arch");
    CampaignEntity campaign = newCampaign(owner, "https://example.com/x");
    archiveCampaign.execute(campaign.getId(), owner);

    assertThatThrownBy(
            () ->
                batchService.create(
                    campaign.getId(),
                    owner,
                    new CampaignBatchCreateRequest("n", null, null, 50, null, null).toCommand()))
        .isInstanceOf(CampaignException.class);
  }

  @Test
  void bulkAllOrNothing_anyInvalidRowAbortsEntireRequest() {
    Long owner = newOwner("bulkbad");
    CampaignEntity campaign = newCampaign(owner, "https://example.com/d");

    CampaignBatchBulkRequest req =
        new CampaignBatchBulkRequest(
            List.of(
                new CampaignBatchCreateRequest("ok", null, null, 100, null, null),
                new CampaignBatchCreateRequest("zero-q", null, null, 0, null, null)));

    assertThatThrownBy(() -> batchService.createBulk(campaign.getId(), owner, req.toCommand()))
        .isInstanceOf(CampaignException.class);

    assertThat(batchService.list(campaign.getId(), owner)).isEmpty();
  }

  @Test
  void bulkCreatesAllBatchesPreservingOrder() {
    Long owner = newOwner("bulkok");
    CampaignEntity campaign = newCampaign(owner, "https://example.com/d");

    CampaignBatchBulkRequest req =
        new CampaignBatchBulkRequest(
            List.of(
                new CampaignBatchCreateRequest("first", "A", "East", 100, null, null),
                new CampaignBatchCreateRequest("second", "B", "West", 200, null, null),
                new CampaignBatchCreateRequest("third", "C", "South", 300, null, null)));

    List<BatchWithLink> created = batchService.createBulk(campaign.getId(), owner, req.toCommand());

    assertThat(created).hasSize(3);
    assertThat(created)
        .extracting(r -> r.batch().getName())
        .containsExactly("first", "second", "third");
  }

  @Test
  void listReturnsOnlyOwnerCampaignBatches() {
    Long owner = newOwner("own");
    Long stranger = newOwner("stranger");
    CampaignEntity mine = newCampaign(owner, "https://example.com/d");
    batchService.create(
        mine.getId(),
        owner,
        new CampaignBatchCreateRequest("n", null, null, 10, null, null).toCommand());

    assertThatThrownBy(() -> batchService.list(mine.getId(), stranger))
        .isInstanceOf(CampaignException.class);
  }
}
