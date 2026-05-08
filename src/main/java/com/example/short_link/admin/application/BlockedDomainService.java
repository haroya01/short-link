package com.example.short_link.admin.application;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.BlockedDomainRepository;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages a small list of domains an administrator has flagged. Used at link-creation time to
 * reject unsafe destinations beyond what Safe Browsing catches (operator policy, abuse, copyright,
 * etc.).
 */
@Service
@RequiredArgsConstructor
public class BlockedDomainService {

  private final BlockedDomainRepository repository;

  @Transactional
  @CacheEvict(value = "blocked-domains", allEntries = true)
  public BlockedDomainEntity block(String rawDomain, String reason, Long actorUserId) {
    String normalized = normalize(rawDomain);
    if (normalized == null) {
      throw new IllegalArgumentException("invalid domain");
    }
    return repository
        .findByDomain(normalized)
        .orElseGet(() -> repository.save(new BlockedDomainEntity(normalized, reason, actorUserId)));
  }

  @Transactional
  @CacheEvict(value = "blocked-domains", allEntries = true)
  public boolean unblock(String rawDomain) {
    String normalized = normalize(rawDomain);
    if (normalized == null) return false;
    return repository
        .findByDomain(normalized)
        .map(
            entity -> {
              repository.delete(entity);
              return true;
            })
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public List<BlockedDomainEntity> list() {
    return repository.findAllByOrderByBlockedAtDesc();
  }

  /**
   * @return true when {@code url}'s host (or a parent domain) is blocked.
   */
  public boolean isBlocked(String url) {
    String host = hostOf(url);
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

  static String normalize(String input) {
    if (input == null) return null;
    String trimmed = input.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) return null;
    String stripped = trimmed.replaceFirst("^https?://", "");
    int slash = stripped.indexOf('/');
    if (slash >= 0) stripped = stripped.substring(0, slash);
    if (stripped.startsWith("www.")) stripped = stripped.substring(4);
    return stripped.isEmpty() ? null : stripped;
  }

  static String hostOf(String url) {
    if (url == null || url.isBlank()) return null;
    try {
      String host = URI.create(url).getHost();
      if (host == null) return null;
      String lower = host.toLowerCase(Locale.ROOT);
      return lower.startsWith("www.") ? lower.substring(4) : lower;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
