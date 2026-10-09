package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.dto.BatchWithLink;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.application.write.CreateCampaignCommand;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.application.write.EndCampaignNowUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.application.read.CachedLinkLoader;
import com.example.short_link.link.application.write.BulkDeleteLinksCommand;
import com.example.short_link.link.application.write.BulkDeleteLinksUseCase;
import com.example.short_link.link.application.write.ClaimAnonymousLinksCommand;
import com.example.short_link.link.application.write.ClaimAnonymousLinksUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class LinkCacheEvictionTest {
  @Autowired private BulkDeleteLinksUseCase bulkDelete;
  @Autowired private LinkProtectionService protection;
  @Autowired private CachedLinkLoader loader;
  @Autowired private CacheManager cacheManager;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CreateCampaignUseCase createCampaign;
  @Autowired private CampaignBatchService campaignBatches;
  @Autowired private EndCampaignNowUseCase endCampaignNow;
  @Autowired private ClaimAnonymousLinksUseCase claimAnonymousLinks;
  @Autowired private UserDeletionService userDeletion;

  private final ExecutorService redirectThread = Executors.newSingleThreadExecutor();
  private final List<Long> createdUsers = new ArrayList<>();
  private final List<String> createdCodes = new ArrayList<>();

  @AfterEach
  void stopRedirectThread() {
    redirectThread.shutdownNow();
  }

  @AfterEach
  void deleteCommittedRows() {
    for (String code : createdCodes) {
      jdbc.update("DELETE FROM link WHERE short_code = ?", code);
    }
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM campaign WHERE owner_id = ?", userId);
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void aRedirectThatReadsBeforeTheDeleteCommitsDoesNotLeaveTheDeletedLinkCached() throws Exception {
    UserEntity owner = newUser();
    ShortCode code = newLink(owner);
    Cache cache = cacheManager.getCache("link");

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              bulkDelete.execute(new BulkDeleteLinksCommand(owner.getId(), List.of(code.value())));
              try {
                redirectThread.submit(() -> loader.loadByShortCode(code)).get();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              awaitCached(cache, code);
            });

    assertThat(cache.get(code)).isNull();
  }

  @Test
  void aCachedLinkIsGoneAsSoonAsSettingItsPasswordReturns() {
    UserEntity owner = newUser();
    Cache cache = cacheManager.getCache("link");
    for (int i = 0; i < 20; i++) {
      ShortCode code = newLink(owner);
      loader.loadByShortCode(code);
      awaitCached(cache, code);

      protection.update(owner.getId(), code, "secret123", null);

      assertThat(cache.get(code)).isNull();
    }
  }

  @Test
  void endingACampaignDropsTheCachedBatchLinkSoTheEndPolicyApplies() {
    UserEntity owner = newUser();
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.REDIRECT);
    ShortCode code = newBatch(owner, campaign).link().getShortCode();
    Cache cache = cacheManager.getCache("link");
    loader.loadByShortCode(code);
    awaitCached(cache, code);

    endCampaignNow.execute(campaign.getId(), owner.getId());

    assertThat(cache.get(code)).isNull();
    assertThat(loader.loadByShortCode(code).expiresAt()).isNotNull();
  }

  @Test
  void deletingACampaignBatchDropsItsCachedLink() {
    UserEntity owner = newUser();
    CampaignEntity campaign = newCampaign(owner, CampaignPostEndAction.KEEP);
    BatchWithLink batch = newBatch(owner, campaign);
    ShortCode code = batch.link().getShortCode();
    Cache cache = cacheManager.getCache("link");
    loader.loadByShortCode(code);
    awaitCached(cache, code);

    campaignBatches.delete(campaign.getId(), batch.batch().getId(), owner.getId());

    assertThat(cache.get(code)).isNull();
  }

  @Test
  void claimingAnAnonymousLinkDropsTheCachedCopyThatStillExpires() {
    UserEntity owner = newUser();
    String token = UUID.randomUUID().toString().replace("-", "");
    LinkEntity anonymous =
        new LinkEntity(
            "https://example.com", newCode(), null, Instant.now().plus(1, ChronoUnit.DAYS));
    anonymous.setClaimToken(token);
    ShortCode code = linkRepository.save(anonymous).getShortCode();
    Cache cache = cacheManager.getCache("link");
    loader.loadByShortCode(code);
    awaitCached(cache, code);

    claimAnonymousLinks.execute(ClaimAnonymousLinksCommand.of(owner.getId(), List.of(token)));

    assertThat(cache.get(code)).isNull();
    assertThat(loader.loadByShortCode(code).expiresAt()).isNull();
  }

  @Test
  void hardDeletingAnAccountDropsItsCachedLinks() {
    UserEntity owner = newUser();
    ShortCode code = newLink(owner);
    Cache cache = cacheManager.getCache("link");
    loader.loadByShortCode(code);
    awaitCached(cache, code);

    userDeletion.hardDelete(owner.getId());

    assertThat(cache.get(code)).isNull();
  }

  private CampaignEntity newCampaign(UserEntity owner, CampaignPostEndAction action) {
    return createCampaign.execute(
        new CreateCampaignCommand(
            owner.getId(),
            "Cache",
            null,
            Instant.now().plus(30, ChronoUnit.DAYS),
            "https://example.com/campaign",
            action,
            action == CampaignPostEndAction.REDIRECT ? "https://example.com/after" : null,
            null));
  }

  private BatchWithLink newBatch(UserEntity owner, CampaignEntity campaign) {
    return campaignBatches.create(
        campaign.getId(),
        owner.getId(),
        new CampaignBatchCreateCommand("Station", null, null, 10, null, null));
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UserEntity user =
        userRepository.save(new UserEntity("cache-" + tag + "@example.com", "google", tag));
    createdUsers.add(user.getId());
    return user;
  }

  private ShortCode newLink(UserEntity owner) {
    String code = newCode();
    linkRepository.save(new LinkEntity("https://example.com", code, owner.getId(), null));
    return new ShortCode(code);
  }

  private String newCode() {
    String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    createdCodes.add(code);
    return code;
  }

  private static void awaitCached(Cache cache, ShortCode code) {
    for (int i = 0; i < 500 && cache.get(code) == null; i++) {
      Thread.onSpinWait();
    }
    assertThat(cache.get(code)).isNotNull();
  }
}
