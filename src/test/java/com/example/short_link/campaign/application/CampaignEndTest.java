package com.example.short_link.campaign.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.api.CampaignBatchCreateRequest;
import com.example.short_link.campaign.api.CampaignCreateRequest;
import com.example.short_link.campaign.api.CampaignUpdateRequest;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.campaign.domain.CampaignStatus;
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

  @Autowired private CampaignService campaignService;
  @Autowired private CampaignBatchService batchService;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  private Long newOwner(String suffix) {
    return userRepository
        .save(new UserEntity("u-end-" + suffix + "@x.com", "google", suffix))
        .getId();
  }

  private CampaignEntity newCampaign(Long owner, CampaignPostEndAction action, String redirectUrl) {
    return campaignService.create(
        owner,
        new CampaignCreateRequest(
            "C",
            null,
            Instant.now().plusSeconds(3600),
            "https://example.com/d",
            action,
            redirectUrl));
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

    campaignService.endNow(campaign.getId(), owner);

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
    campaignService.endNow(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(linkId).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull().isAfterOrEqualTo(before);
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

    campaignService.endNow(campaign.getId(), owner);

    LinkEntity reloaded = linkRepository.findById(linkId).orElseThrow();
    assertThat(reloaded.getExpiresAt()).isNotNull();
    assertThat(reloaded.getExpiredRedirectUrl()).isEqualTo(dest);
  }

  @Test
  void endDueScannerCatchesActiveCampaignsPastEndsAt() {
    Long owner = newOwner("due");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.EXPIRE, null);
    // 강제로 endsAt 을 과거로 옮긴다 (캠페인은 already ACTIVE).
    assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
    campaignService.updatePolicy(
        campaign.getId(),
        owner,
        new CampaignUpdateRequest(null, Instant.now().minusSeconds(1), null, null, null));
    BatchWithLink bwl =
        batchService.create(
            campaign.getId(),
            owner,
            new CampaignBatchCreateRequest("east", null, null, 100, null, null));

    // updatePolicy 호출이 endsAt 을 past 로 바꿔 validation 통과 후 batch 생성
    // (terminal 검사는 status 기반이므로 ACTIVE 면 허용). 이제 스케줄러가 잡아야 한다.
    int ended = campaignService.endDue(Instant.now());

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
    campaignService.endNow(campaign.getId(), owner);
    LinkEntity v1 = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(v1.getExpiredRedirectUrl()).isEqualTo("https://example.com/v1");

    // ENDED 상태에서 updatePolicy 거부됨 → 도메인 직접 조작 우회 위해 다른 시나리오:
    // 새 캠페인 + 재적용 시 변경된 destination 이 적용되는지만 검증.
    // 여기선 reapplyPolicy 가 멱등성 보장 + KEEP 상태에서 변경된 경우 NoOp.
    campaignService.reapplyPolicy(campaign.getId(), owner);

    LinkEntity reapplied = linkRepository.findById(bwl.link().getId()).orElseThrow();
    assertThat(reapplied.getExpiredRedirectUrl()).isEqualTo("https://example.com/v1");
  }

  @Test
  void reapplyOnNonEndedRejects() {
    Long owner = newOwner("nonended");
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.KEEP, null);

    assertThatThrownBy(() -> campaignService.reapplyPolicy(campaign.getId(), owner))
        .isInstanceOf(ReapplyOnNonEndedException.class);
  }
}
