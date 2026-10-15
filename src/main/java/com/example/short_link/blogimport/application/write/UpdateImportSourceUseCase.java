package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.read.ImportSourceQueryService;
import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.application.write.ImportedPostWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateImportSourceUseCase {

  private final ImportSourceRepository sources;
  private final ImportedPostWriter posts;
  private final ImportSourceQueryService views;

  @Transactional
  public ImportSourceView execute(
      Long userId, Long sourceId, Boolean announceNewPosts, Boolean showInDiscover) {
    ImportSourceEntity source =
        sources
            .findByIdForUpdate(sourceId)
            .filter(s -> s.isOwnedBy(userId))
            .orElseThrow(
                () ->
                    new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND, sourceId));
    boolean shown = source.isShowInDiscover();
    source.updateSettings(announceNewPosts, showInDiscover);
    if (shown != source.isShowInDiscover()) {
      posts.hideFromDiscover(source.getId(), !source.isShowInDiscover());
    }
    return views.viewOf(sources.save(source));
  }
}
