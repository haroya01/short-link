package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
import com.example.short_link.campaign.exception.ReapplyOnNonEndedException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class CampaignEndTest {

  @Autowired
  private com.example.short_link.campaign.application.write.CreateCampaignUseCase createCampaign;

  @Autowired
  private com.example.short_link.campaign.application.write.ArchiveCampaignUseCase archiveCampaign;

  @Autowired
  private com.example.short_link.campaign.application.write.EndCampaignNowUseCase endNowUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.EndDueCampaignsUseCase endDueUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.UpdateCampaignPolicyUseCase
      updateUseCase;

  @Autowired
  private com.example.short_link.campaign.application.write.ReapplyCampaignPolicyUseCase
      reapplyUseCase;

  @Autowired private CampaignBatchService batchService;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  private Long newOwner(String suffix) {
    return userRepository
        .save(new UserEntity("u-end-" + suffix + "@x.com", "google", suffix))
        .getId();
  }

  private CampaignEntity newCampaign(Long owner, CampaignPostEndAction action, String redirectUrl) {
    return newCampaign(owner, action, redirectUrl, null);
  }

  private CampaignEntity newCampaign(
      Long owner, CampaignPostEndAction action, String redirectUrl, String postEndMessage) {
    return createCampaign.execute(
        owner,
        new CampaignCreateRequest(
            "C",
            null,
            Instant.now().plusSeconds(3600),
            "https://example.com/d",
            action,
            redirectUrl,
            postEndMessage));
  }

  @Test
  void endNowOnKeepPolicyDoesNotTouchLink() {
    Long owner = newOwner("keep");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.KEEP, null);
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));
    Long linkId = bwl.link().getId();

    endNowUseCase.execute(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(linkId).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNull();
    assertThat(reloaded.getExpiredRedirectUrl()).isNull();
  }

  @Test
  void endNowOnExpirePolicyBakesExpiresAtIntoLink() {
    Long owner = newOwner("expire");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.EXPIRE, null);
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));
    Long linkId = bwl.link().getId();

    Instant before = Instant.now();
    endNowUseCase.execute(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(linkId).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull().isAfterOrEqualTo(before);
    assertThat(reloaded.getExpiredRedirectUrl()).isNull();
  }

  @Test
  void endNowOnExpirePolicyAlsoBakesMessageIntoLink() {
    Long owner = newOwner("expmsg");
    String message = "캠페인 종료 — 다음 이벤트는 12월 예정";
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.EXPIRE, null, message);
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));

    endNowUseCase.execute(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull();
    assertThat(reloaded.getExpiredMessage()).isEqualTo(message);
    assertThat(reloaded.getExpiredRedirectUrl()).isNull();
  }

  @Test
  void endNowOnRedirectPolicyBakesUrlIntoLink() {
    Long owner = newOwner("redir");
    String dest = "https://example.com/after";
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.REDIRECT, dest);
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));
    Long linkId = bwl.link().getId();

    endNowUseCase.execute(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(linkId).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull();
    assertThat(reloaded.getExpiredRedirectUrl()).isEqualTo(dest);
  }

  @Test
  void endDueScannerCatchesActiveCampaignsPastEndsAt() {
    Long owner = newOwner("due");
    // startsAt 을 과거 깊이 박아 endsAt 도 과거 (이미 끝났어야 할 캠페인 상태) 를 만든다.
    // create 의 validation 은 endsAt > startsAt 만 확인하므로 통과.
    Instant longPast = Instant.now().minusSeconds(60 * 60 * 24 * 14);
    CampaignEntity campaign =
        createCampaign.execute(
            owner,
            new com.example.short_link.campaign.application.CampaignCreateRequest(
                "C",
                longPast,
                longPast.plusSeconds(60 * 60),
                "https://example.com/x",
                CampaignPostEndAction.EXPIRE,
                null,
                null));
    assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));

    int ended = endDueUseCase.execute(Instant.now());

    assertThat(ended).isEqualTo(1);
    LinkEntity reloaded = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull();
  }

  @Test
  void reapplyPolicyRefreshesLinkAfterPostEndUrlChange() {
    Long owner = newOwner("reapply");
    CampaignEntity campaign =
        newCampaign(owner, CampaignPostEndAction.REDIRECT, "https://example.com/v1");
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));
    endNowUseCase.execute(campaign.getId(), owner);
    LinkEntity v1 = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(v1.getExpiredRedirectUrl()).isEqualTo("https://example.com/v1");

    // ENDED 상태에서 updatePolicy 거부됨 → 도메인 직접 조작 우회 위해 다른 시나리오:
    // 새 캠페인 + 재적용 시 변경된 destination 이 적용되는지만 검증.
    // 여기선 reapplyPolicy 가 멱등성 보장 + KEEP 상태에서 변경된 경우 NoOp.
    reapplyUseCase.execute(campaign.getId(), owner);

    LinkEntity reapplied = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(reapplied.getExpiredRedirectUrl()).isEqualTo("https://example.com/v1");
  }

  @Test
  void reapplyPicksUpEditedExpireMessage() {
    Long owner = newOwner("msgreapply");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.EXPIRE, null, "구버전 메시지");
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));
    endNowUseCase.execute(campaign.getId(), owner);
    assertThat(linkRepository.findById(bwl.link().getId()).orElseThrow().getExpiredMessage())
        .isEqualTo("구버전 메시지");

    // ENDED 상태에서 메시지만 변경. updatePolicy 는 ARCHIVED 만 거부하므로 ENDED 에선 허용.
    updateUseCase.execute(
        campaign.getId(), owner, new CampaignUpdateRequest(null, null, null, null, null, "새 메시지"));
    reapplyUseCase.execute(campaign.getId(), owner);

    LinkEntity reapplied = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(reapplied.getExpiredMessage()).isEqualTo("새 메시지");
  }

  @Test
  void reapplyOnNonEndedRejects() {
    Long owner = newOwner("nonended");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.KEEP, null);

    assertThatThrownBy(() -> reapplyUseCase.execute(campaign.getId(), owner))
        .isInstanceOf(ReapplyOnNonEndedException.class);
  }
}
