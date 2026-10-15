package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.application.write.ImportedPostWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DisconnectImportSourceUseCase {

  private final ImportSourceRepository sources;
  private final ImportedPostWriter posts;
  private final TransactionTemplate tx;

  public DisconnectImportSourceUseCase(
      ImportSourceRepository sources,
      ImportedPostWriter posts,
      PlatformTransactionManager transactionManager) {
    this.sources = sources;
    this.posts = posts;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public void execute(Long userId, Long sourceId, boolean keepPosts) {
    tx.executeWithoutResult(
        status -> {
          ImportSourceEntity source = locked(userId, sourceId);
          source.disconnect();
          sources.save(source);
        });
    if (keepPosts) {
      return;
    }
    posts.deleteAllFrom(userId, sourceId);
    tx.executeWithoutResult(status -> sources.delete(locked(userId, sourceId)));
  }

  private ImportSourceEntity locked(Long userId, Long sourceId) {
    return sources
        .findByIdForUpdate(sourceId)
        .filter(s -> s.isOwnedBy(userId))
        .orElseThrow(
            () -> new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND, sourceId));
  }
}
