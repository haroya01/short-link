package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import com.example.short_link.common.security.BlockedDomainChecker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockedDomainQueryService implements BlockedDomainChecker {

  private final BlockedDomainRepository repository;
  private final BlockedDomainCache blockedDomainCache;

  @Transactional(readOnly = true)
  public List<BlockedDomainEntity> list() {
    return repository.findAllByOrderByBlockedAtDesc();
  }

  /**
   * @return true when {@code url}'s host (or a parent domain) is blocked.
   */
  public boolean isBlocked(String url) {
    String host = BlockedDomainNormalizer.hostOf(url);
    if (host == null) return false;
    var blocked = blockedDomainCache.currentBlockedSet();
    if (blocked.isEmpty()) return false;
    String walk = host;
    while (walk != null && !walk.isEmpty()) {
      if (blocked.contains(walk)) return true;
      int dot = walk.indexOf('.');
      if (dot < 0) return false;
      walk = walk.substring(dot + 1);
    }
    return false;
  }
}
