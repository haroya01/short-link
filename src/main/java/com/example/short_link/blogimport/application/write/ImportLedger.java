package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.repository.ImportItemRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportLedger {

  private final ImportItemRepository items;
  private final Clock clock;

  @Transactional(readOnly = true)
  public Map<String, ImportItemEntity> taken(Long sourceId, Collection<String> urlHashes) {
    Map<String, ImportItemEntity> byHash = new HashMap<>();
    for (ImportItemEntity item : items.findBySourceIdAndUrlHashIn(sourceId, urlHashes)) {
      byHash.put(item.getUrlHash(), item);
    }
    return byHash;
  }

  @Transactional
  public Long claim(Long sourceId, String urlHash) {
    return items.saveAndFlush(new ImportItemEntity(sourceId, urlHash)).getId();
  }

  @Transactional
  public void attach(Long itemId, Long postId) {
    items.findById(itemId).ifPresent(item -> item.attachPost(postId));
  }

  @Transactional
  public void fail(Long itemId) {
    items.findById(itemId).ifPresent(ImportItemEntity::fail);
  }

  @Transactional
  public void complete(Long itemId) {
    items.findById(itemId).ifPresent(item -> item.complete(clock.instant()));
  }
}
