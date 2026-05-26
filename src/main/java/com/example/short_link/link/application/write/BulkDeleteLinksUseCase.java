package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BulkDeleteLinksUseCase {

  private final LinkRepository repository;
  private final AuditLogService auditLogService;
  private final CacheManager cacheManager;

  @Transactional
  public int execute(BulkDeleteLinksCommand command) {
    if (command.shortCodes().isEmpty()) return 0;
    List<LinkEntity> owned =
        command.shortCodes().stream()
            .distinct()
            .map(repository::findByShortCode)
            .flatMap(Optional::stream)
            .filter(l -> l.isOwnedBy(command.userId()))
            .toList();
    if (owned.isEmpty()) return 0;
    repository.deleteAll(owned);
    Cache cache = cacheManager.getCache("link");
    for (LinkEntity link : owned) {
      if (cache != null) cache.evict(link.getShortCode());
      auditLogService.record(
          AuditAction.LINK_DELETED,
          "link",
          link.getShortCode().value(),
          command.userId(),
          Map.of("bulk", true));
    }
    return owned.size();
  }
}
