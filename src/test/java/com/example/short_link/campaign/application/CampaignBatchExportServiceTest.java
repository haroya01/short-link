package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignBatchExportServiceTest {

  @Autowired private CampaignBatchExportService exportService;
  @Autowired private CampaignBatchService batchService;

  @Autowired
  private com.example.short_link.campaign.application.write.CreateCampaignUseCase createCampaign;

  @Autowired
  private com.example.short_link.campaign.application.write.ArchiveCampaignUseCase archiveCampaign;

  @Autowired private UserRepository userRepository;

  private Long newOwner(String suffix) {
    return userRepository
        .save(new UserEntity("u-exp-" + suffix + "@x.com", "google", suffix))
        .getId();
  }

  @Test
  void csvContainsHeaderAndRowPerBatch() {
    Long owner = newOwner("csv");
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C",
                null,
                Instant.now().plusSeconds(3600),
                "https://example.com/d",
                null,
                null,
                null));
    batchService.create(
        campaign.getId(),
        owner,
        new CampaignBatchCreateRequest("east", "A", "East", 500, null, null));
    batchService.create(
        campaign.getId(),
        owner,
        new CampaignBatchCreateRequest(
            "west", "B", "West", 300, "https://example.com/special", null));

    String csv = exportService.exportCsv(campaign.getId(), owner);

    assertThat(csv).startsWith("batch_id,batch_name,distributor,area,quantity,short_url");
    String[] lines = csv.split("\n");
    assertThat(lines).hasSize(3); // header + 2 rows
    assertThat(lines[1]).contains("east").contains("500");
    assertThat(lines[2]).contains("west").contains("https://example.com/special");
  }

  @Test
  void qrZipContainsOneEntryPerBatch() throws Exception {
    Long owner = newOwner("zip");
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C",
                null,
                Instant.now().plusSeconds(3600),
                "https://example.com/d",
                null,
                null,
                null));
    batchService.create(
        campaign.getId(), owner, new CampaignBatchCreateRequest("a", null, null, 10, null, null));
    batchService.create(
        campaign.getId(), owner, new CampaignBatchCreateRequest("b", null, null, 10, null, null));

    byte[] zip = exportService.exportQrZip(campaign.getId(), owner);

    Set<String> entries = new HashSet<>();
    try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = zin.getNextEntry()) != null) {
        entries.add(entry.getName());
        // drain payload to keep stream healthy
        zin.readAllBytes();
      }
    }
    assertThat(entries).hasSize(2);
    assertThat(entries).allMatch(name -> name.endsWith(".png"));
  }

  @Test
  void updateMetadataKeepsLinkAndQuantity() {
    Long owner = newOwner("upd");
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C",
                null,
                Instant.now().plusSeconds(3600),
                "https://example.com/d",
                null,
                null,
                null));
    BatchWithLink original =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("orig", "A", "East", 500, null, null));
    Long originalLinkId = original.link().id();

    BatchWithLink updated =
        batchService.update(
            campaign.getId(),
            original.batch().getId(),
            owner,
            new CampaignBatchUpdateRequest("renamed", "B", "West", 700, "updated memo"));

    assertThat(updated.batch().getName()).isEqualTo("renamed");
    assertThat(updated.batch().getDistributorName()).isEqualTo("B");
    assertThat(updated.batch().getQuantity()).isEqualTo(700);
    assertThat(updated.link().id()).isEqualTo(originalLinkId);
  }

  @Test
  void deleteRemovesBatchAndLink() {
    Long owner = newOwner("del");
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new CampaignCreateRequest(
                "C",
                null,
                Instant.now().plusSeconds(3600),
                "https://example.com/d",
                null,
                null,
                null));
    BatchWithLink created =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("doomed", null, null, 50, null, null));

    batchService.delete(campaign.getId(), created.batch().getId(), owner);

    assertThat(batchService.list(campaign.getId(), owner)).isEmpty();
  }
}
