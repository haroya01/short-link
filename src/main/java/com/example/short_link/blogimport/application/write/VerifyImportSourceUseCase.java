package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.application.BlogFeed;
import com.example.short_link.blogimport.application.BlogFeeds;
import com.example.short_link.blogimport.application.read.ImportSourceQueryService;
import com.example.short_link.blogimport.application.read.ImportSourceView;
import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import com.example.short_link.post.domain.SourceUrlHash;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VerifyImportSourceUseCase {

  private final BlogFeeds feeds;
  private final ImportSourceRepository sources;
  private final ImportLedger ledger;
  private final ImportSourceQueryService views;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final TransactionTemplate tx;

  public VerifyImportSourceUseCase(
      BlogFeeds feeds,
      ImportSourceRepository sources,
      ImportLedger ledger,
      ImportSourceQueryService views,
      ApplicationEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.feeds = feeds;
    this.sources = sources;
    this.ledger = ledger;
    this.views = views;
    this.events = events;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public ImportSourceView execute(Long userId, Long sourceId) {
    ImportSourceEntity source = owned(sources.findById(sourceId).orElse(null), userId, sourceId);
    if (source.isVerified()) {
      return views.viewOf(source);
    }
    String code = requireCode(source);
    BlogFeed feed = feeds.read(source.address());
    if (!feed.describes(code)) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING, code);
    }
    try {
      return tx.execute(status -> verify(userId, sourceId, code, feed));
    } catch (DataIntegrityViolationException claimed) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
    }
  }

  private ImportSourceView verify(Long userId, Long sourceId, String code, BlogFeed feed) {
    ImportSourceEntity source =
        owned(sources.findByIdForUpdate(sourceId).orElse(null), userId, sourceId);
    if (source.isVerified()) {
      return views.viewOf(source);
    }
    if (!code.equals(requireCode(source))) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING, code);
    }
    if (sources.isClaimedByAnother(source.getBlogKey(), userId)) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_TAKEN);
    }
    Set<String> hashes = new LinkedHashSet<>();
    feed.posts().forEach(post -> hashes.add(SourceUrlHash.of(post.url())));
    Map<String, ImportItemEntity> taken = ledger.taken(source.getId(), hashes);
    int pending =
        (int)
            hashes.stream().filter(h -> taken.get(h) == null || !taken.get(h).isSettled()).count();
    source.refresh(feed.title(), feed.fullContent());
    source.verify(clock.instant(), pending);
    sources.saveAndFlush(source);
    events.publishEvent(new ImportSourceVerified(source.getId()));
    return views.viewOf(source);
  }

  private static ImportSourceEntity owned(ImportSourceEntity source, Long userId, Long sourceId) {
    if (source == null || !source.isOwnedBy(userId)) {
      throw new BlogImportException(BlogImportErrorCode.IMPORT_SOURCE_NOT_FOUND, sourceId);
    }
    return source;
  }

  private static String requireCode(ImportSourceEntity source) {
    if (!source.awaitsVerification() || source.getVerificationCode() == null) {
      throw new BlogImportException(
          BlogImportErrorCode.IMPORT_VERIFICATION_CODE_MISSING, "a verification code");
    }
    return source.getVerificationCode();
  }
}
