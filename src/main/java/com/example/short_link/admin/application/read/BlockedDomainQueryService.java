package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side of the operator-managed block list. {@link #isBlocked(String)} is called per
 * link-create so the {@code blocked-domains} cache fronts the {@code SELECT *} that backs the full
 * set — write paths evict the cache on block / unblock.
 */
@Service
@RequiredArgsConstructor
public class BlockedDomainQueryService {

  private final BlockedDomainRepository repository;

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
    Set<String> blocked = currentBlockedSet();
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

  @Cacheable("blocked-domains")
  public Set<String> currentBlockedSet() {
    return Set.copyOf(repository.findAll().stream().map(BlockedDomainEntity::getDomain).toList());
  }
}
