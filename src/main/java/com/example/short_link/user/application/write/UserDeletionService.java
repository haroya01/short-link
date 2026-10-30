package com.example.short_link.user.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.UserFolder;
import com.example.short_link.common.user.UserDataEraser;
import com.example.short_link.link.application.LinkAccountData;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {

  private final UserRepository userRepository;
  private final LinkAccountData linkAccountData;
  private final FollowRepository followRepository;
  private final BlockRepository blockRepository;
  private final MuteRepository muteRepository;
  private final WebPushSubscriptionRepository webPushSubscriptionRepository;
  private final List<UserDataEraser> userDataErasers;
  private final RefreshTokenStore refreshTokenStore;
  private final DeviceTokenRepository deviceTokens;
  private final MeterRegistry meterRegistry;
  private final AuditLogService auditLogService;
  private final ApplicationEventPublisher events;
  private final ProfileCacheInvalidator profileCache;
  private final ObjectStorage storage;
  private final TransactionTemplate transaction;

  @Transactional
  public void deleteAccount(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) {
      return;
    }
    user.softDelete();
    profileCache.evictByUsername(user.getUsername());
    refreshTokenStore.deleteAllForUser(userId);
    deviceTokens.deleteByUserId(userId);
    events.publishEvent(new AccountDeletedEvent(userId));

    long ownedLinks = linkAccountData.countOwned(userId);
    meterRegistry.counter("user.soft_deleted").increment();
    auditLogService.record(
        AuditAction.USER_DELETED,
        "user",
        String.valueOf(userId),
        userId,
        Map.of("soft", true, "ownedLinks", ownedLinks));
    log.info("user {} soft-deleted (links retained: {})", userId, ownedLinks);
  }

  public void hardDelete(Long userId) {
    if (Boolean.TRUE.equals(transaction.execute(status -> purge(userId)))) {
      emptyFoldersOf(userId);
    }
  }

  private boolean purge(Long userId) {
    if (!userRepository.existsById(userId)) return false;
    // Purge slice-owned rows before users: their foreign keys lack ON DELETE CASCADE.
    userDataErasers.forEach(eraser -> eraser.eraseFor(userId));
    followRepository.deleteAllInvolving(userId);
    blockRepository.deleteAllInvolving(userId);
    muteRepository.deleteAllInvolving(userId);
    // Web-push subscriptions have no users FK, so delete their endpoint and keys explicitly.
    webPushSubscriptionRepository.deleteByUserId(userId);

    int deletedLinks = linkAccountData.eraseOwned(userId);
    refreshTokenStore.deleteAllForUser(userId);
    userRepository.deleteById(userId);
    meterRegistry
        .counter("user.hard_deleted", "links_removed", String.valueOf(deletedLinks))
        .increment();
    return true;
  }

  private void emptyFoldersOf(long userId) {
    if (!storage.isConfigured()) return;
    for (UserFolder folder : UserFolder.values()) {
      try {
        storage.deleteFolder(folder.of(userId));
      } catch (ObjectStorageException e) {
        log.warn("failed to empty a folder of a hard-deleted user folder={}", folder.of(userId), e);
      }
    }
  }
}
