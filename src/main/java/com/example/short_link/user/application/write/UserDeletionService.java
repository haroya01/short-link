package com.example.short_link.user.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) {
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
