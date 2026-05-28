package com.example.short_link.admin.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.read.BlockedDomainCache;
import com.example.short_link.admin.application.read.BlockedDomainQueryService;
import com.example.short_link.admin.application.write.BlockDomainUseCase;
import com.example.short_link.admin.application.write.UnblockDomainUseCase;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.infrastructure.persistence.JpaBlockedDomainRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class BlockedDomainServiceTest {

  private static final String BLOCKED_DOMAINS_CACHE = "blocked-domains";

  @Autowired private BlockDomainUseCase blockDomain;
  @Autowired private UnblockDomainUseCase unblockDomain;
  @Autowired private BlockedDomainQueryService queryService;
  @Autowired private BlockedDomainCache blockedDomainCache;
  @Autowired private JpaBlockedDomainRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    repository.deleteAll();
    clearBlockedDomainCache();
  }

  @AfterEach
  void tearDown() {
    clearBlockedDomainCache();
    repository.deleteAll();
  }

  @Test
  void normalizesAndStores() {
    blockDomain.execute("https://www.bad.example.com/path", "abuse", 1L);
    assertThat(repository.findByDomain("bad.example.com")).isPresent();
    assertThat(repository.findByDomain("www.bad.example.com")).isEmpty();
  }

  @Test
  void detectsBlockedHost() {
    blockDomain.execute("blocked.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://blocked.test/foo")).isTrue();
    assertThat(queryService.isBlocked("https://allowed.example.com/")).isFalse();
  }

  @Test
  void detectsBlockedSubdomain() {
    blockDomain.execute("badco.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://www.badco.test/")).isTrue();
    assertThat(queryService.isBlocked("https://promo.subdomain.badco.test/")).isTrue();
  }

  @Test
  void unblockRemoves() {
    blockDomain.execute("toremove.test", null, 1L);
    assertThat(queryService.isBlocked("https://toremove.test/")).isTrue();
    assertThat(unblockDomain.execute("toremove.test")).isTrue();
    assertThat(queryService.isBlocked("https://toremove.test/")).isFalse();
  }

  @Test
  void cachedEmptySetStaysUntilExplicitEviction() {
    assertThat(queryService.isBlocked("https://late-blocked.test/")).isFalse();

    repository.saveAndFlush(new BlockedDomainEntity("late-blocked.test", "direct", 1L));

    assertThat(queryService.isBlocked("https://late-blocked.test/")).isFalse();

    clearBlockedDomainCache();

    assertThat(queryService.isBlocked("https://late-blocked.test/")).isTrue();
  }

  @Test
  void cacheHitKeepsServingSnapshotUntilBlockUseCaseEvicts() {
    repository.saveAndFlush(new BlockedDomainEntity("first.test", "direct", 1L));
    assertThat(queryService.isBlocked("https://first.test/")).isTrue();

    repository.saveAndFlush(new BlockedDomainEntity("second.test", "direct", 1L));

    assertThat(queryService.isBlocked("https://second.test/")).isFalse();

    blockDomain.execute("second.test", "evict existing", 1L);

    assertThat(queryService.isBlocked("https://second.test/")).isTrue();
  }

  @Test
  void blockUseCaseEvictsCachedAllowedDecision() {
    assertThat(queryService.isBlocked("https://fresh.test/")).isFalse();

    blockDomain.execute("fresh.test", "spam", 1L);

    assertThat(queryService.isBlocked("https://fresh.test/")).isTrue();
  }

  @Test
  void blockUseCaseEvictsCachedAllowedDecisionAfterCommit() {
    assertThat(queryService.isBlocked("https://commit-visible.test/")).isFalse();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              blockDomain.execute("commit-visible.test", "spam", 1L);
              assertThat(queryService.isBlocked("https://commit-visible.test/")).isFalse();
            });

    assertThat(queryService.isBlocked("https://commit-visible.test/")).isTrue();
  }

  @Test
  void rolledBackBlockDoesNotEvictExistingSnapshot() {
    assertThat(queryService.isBlocked("https://after-rollback.test/")).isFalse();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              blockDomain.execute("rolled-back.test", "spam", 1L);
              status.setRollbackOnly();
            });

    assertThat(repository.findByDomain("rolled-back.test")).isEmpty();

    repository.saveAndFlush(new BlockedDomainEntity("after-rollback.test", "direct", 1L));
    assertThat(queryService.isBlocked("https://after-rollback.test/")).isFalse();

    clearBlockedDomainCache();
    assertThat(queryService.isBlocked("https://after-rollback.test/")).isTrue();
  }

  @Test
  void unblockUseCaseEvictsCachedBlockedDecision() {
    blockDomain.execute("gone.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://gone.test/")).isTrue();

    assertThat(unblockDomain.execute("gone.test")).isTrue();

    assertThat(queryService.isBlocked("https://gone.test/")).isFalse();
  }

  @Test
  void cacheSupportsParentDomainWalkAfterDeserialization() {
    blockDomain.execute("parent.test", "spam", 1L);
    assertThat(queryService.isBlocked("https://deep.sub.parent.test/path")).isTrue();

    assertThat(queryService.isBlocked("https://deep.sub.parent.test/path")).isTrue();
    assertThat(blockedDomainCache.currentBlockedSet().domains()).containsExactly("parent.test");
  }

  @Test
  void invalidOrHostlessUrlsDoNotMatchCachedSet() {
    blockDomain.execute("blocked.test", "spam", 1L);
    assertThat(queryService.isBlocked(null)).isFalse();
    assertThat(queryService.isBlocked("  ")).isFalse();
    assertThat(queryService.isBlocked("not a url")).isFalse();
    assertThat(queryService.isBlocked("mailto:user@blocked.test")).isFalse();
  }

  @Test
  void normalizeStripsScheme() {
    assertThat(BlockedDomainNormalizer.normalize("https://www.example.com/path"))
        .isEqualTo("example.com");
    assertThat(BlockedDomainNormalizer.normalize("EXAMPLE.com")).isEqualTo("example.com");
    assertThat(BlockedDomainNormalizer.normalize("  ")).isNull();
    assertThat(BlockedDomainNormalizer.normalize(null)).isNull();
  }

  private void clearBlockedDomainCache() {
    assertThat(BLOCKED_DOMAINS_CACHE).isEqualTo(BlockedDomainCache.CACHE_NAME);
    blockedDomainCache.evictNow();
  }
}
