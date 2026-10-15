package com.example.short_link.blogimport.application.read;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.application.read.ImportedPostQueryService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportSourceQueryService {

  private final ImportSourceRepository sources;
  private final ImportedPostQueryService importedPosts;

  public List<ImportSourceView> list(Long userId) {
    List<ImportSourceEntity> owned = sources.findAllByUserId(userId);
    if (owned.isEmpty()) {
      return List.of();
    }
    Map<Long, Long> counts =
        importedPosts.publishedCounts(owned.stream().map(ImportSourceEntity::getId).toList());
    return owned.stream()
        .map(source -> ImportSourceView.of(source, counts.getOrDefault(source.getId(), 0L)))
        .toList();
  }

  public ImportSourceView get(Long userId, Long sourceId) {
    ImportSourceEntity source =
        sources
            .findById(sourceId)
            .filter(s -> s.isOwnedBy(userId))
            .orElseThrow(
                () ->
                    new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND, sourceId));
    return viewOf(source);
  }

  public ImportSourceView viewOf(ImportSourceEntity source) {
    return ImportSourceView.of(
        source,
        importedPosts.publishedCounts(List.of(source.getId())).getOrDefault(source.getId(), 0L));
  }
}
