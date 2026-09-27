package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.application.read.CachedLinkLoader;
import com.example.short_link.link.application.write.BulkDeleteLinksCommand;
import com.example.short_link.link.application.write.BulkDeleteLinksUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

  private final ExecutorService redirectThread = Executors.newSingleThreadExecutor();
  private final List<Long> createdUsers = new ArrayList<>();

  @AfterEach
  void stopRedirectThread() {
    redirectThread.shutdownNow();
  }

  @AfterEach
  void deleteCommittedRows() {
    for (Long userId : createdUsers) {
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

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UserEntity user =
        userRepository.save(new UserEntity("cache-" + tag + "@example.com", "google", tag));
    createdUsers.add(user.getId());
    return user;
  }

  private ShortCode newLink(UserEntity owner) {
    String code = "c" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    linkRepository.save(new LinkEntity("https://example.com", code, owner.getId(), null));
    return new ShortCode(code);
  }

  private static void awaitCached(Cache cache, ShortCode code) {
    for (int i = 0; i < 500 && cache.get(code) == null; i++) {
      Thread.onSpinWait();
    }
    assertThat(cache.get(code)).isNotNull();
  }
}
