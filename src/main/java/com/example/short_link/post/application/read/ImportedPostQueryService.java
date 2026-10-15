package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.repository.ImportedPostRepository;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportedPostQueryService {

  private final ImportedPostRepository importedPosts;

  public Map<Long, Long> publishedCounts(Collection<Long> importSourceIds) {
    return importedPosts.countPublishedByImportSourceIds(importSourceIds);
  }
}
