package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.read.ImportSourceQueryService;
import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.domain.BlogAddress;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.common.user.UserModerationGuard;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConnectImportSourceUseCase {

  public static final int MAX_SOURCES = 10;

  private final BlogFeeds feeds;
  private final ImportSourceRepository sources;
  private final ImportSourceQueryService views;
  private final UserModerationGuard moderation;
  private final TransactionTemplate tx;

  public ConnectImportSourceUseCase(
      BlogFeeds feeds,
      ImportSourceRepository sources,
      ImportSourceQueryService views,
      UserModerationGuard moderation,
      PlatformTransactionManager transactionManager) {
    this.feeds = feeds;
    this.sources = sources;
    this.views = views;
    this.moderation = moderation;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public ImportSourceView execute(Long userId, String url) {
    moderation.requireCanWrite(userId);
    BlogAddress parsed = feeds.parse(url);
    BlogFeed feed = feeds.readRecent(parsed);
    try {
      return tx.execute(status -> connect(userId, feed));
    } catch (DataIntegrityViolationException raced) {
      return tx.execute(
          status ->
              sources
                  .findByUserIdAndBlogKey(userId, feed.address().blogKey())
                  .map(views::viewOf)
                  .orElseThrow(() -> raced));
    }
  }

  private ImportSourceView connect(Long userId, BlogFeed feed) {
    BlogAddress address = feed.address();
    if (sources.isClaimedByAnother(address.blogKey(), userId)) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
    }
    Optional<ImportSourceEntity> existing =
        sources.findByUserIdAndBlogKey(userId, address.blogKey());
    if (existing.isPresent()) {
      ImportSourceEntity source = existing.get();
      if (source.isDisconnected()) {
        requireRoom(userId);
        source.reconnect(VerificationCodes.next(), feed.title(), feed.fullContent());
        sources.saveAndFlush(source);
      }
      return views.viewOf(source);
    }
    requireRoom(userId);
    ImportSourceEntity created =
        sources.saveAndFlush(
            new ImportSourceEntity(
                userId, address, feed.title(), feed.fullContent(), VerificationCodes.next()));
    return views.viewOf(created);
  }

  private void requireRoom(Long userId) {
    if (sources.countActiveByUserId(userId) >= MAX_SOURCES) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_LIMIT, MAX_SOURCES);
    }
  }
}
