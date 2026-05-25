package com.example.short_link.user.application;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.RefreshTokenStore;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soft-deletes the user — marks {@code deleted_at} and wipes refresh tokens. Owned links remain in
 * place but the redirect path treats them as gone (410). Within the configurable grace window the
 * user can log in again to restore everything; after the window {@link
 * com.example.short_link.user.application.SoftDeletedUserCleanupJob} hard-deletes the row (link /
 * click_event / api_key / link_tag CASCADE).
 *
 * <p>Anonymous links (user_id IS NULL) are untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final ClickEventRepository clickEventRepository;
  private final RefreshTokenStore refreshTokenStore;
  private final MeterRegistry meterRegistry;
  private final AuditLogService auditLogService;

  @Transactional
  public void deleteAccount(Long userId) {
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.isDeleted()) {
      // already pending deletion — idempotent
      return;
    }
    user.softDelete();
    refreshTokenStore.deleteAllForUser(userId);

    long ownedLinks = linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId).size();
    meterRegistry.counter("user.soft_deleted").increment();
    auditLogService.record(
        AuditAction.USER_DELETED,
        "user",
        String.valueOf(userId),
        userId,
        Map.of("soft", true, "ownedLinks", ownedLinks));
    log.info("user {} soft-deleted (links retained: {})", userId, ownedLinks);
  }

  /**
   * Permanently removes a user and their owned data. Called by the scheduled cleanup job after the
   * grace window expires. Anonymous links remain untouched.
   */
  @Transactional
  public void hardDelete(Long userId) {
    if (!userRepository.existsById(userId)) return;

    List<LinkEntity> links = linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (!links.isEmpty()) {
      List<Long> linkIds = links.stream().map(LinkEntity::getId).toList();
      int deletedClicks = clickEventRepository.deleteByLinkIds(linkIds);
      log.info("user {} hard-delete: {} click events removed", userId, deletedClicks);
    }
    int deletedLinks = linkRepository.deleteByUserId(userId);
    refreshTokenStore.deleteAllForUser(userId);
    userRepository.deleteById(userId);
    meterRegistry
        .counter("user.hard_deleted", "links_removed", String.valueOf(deletedLinks))
        .increment();
  }
}
