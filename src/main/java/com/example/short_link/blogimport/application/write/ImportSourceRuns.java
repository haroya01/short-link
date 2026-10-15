package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ImportSourceRuns {

  private final ImportSourceRepository sources;
  private final Clock clock;

  @Transactional
  public boolean start(Long sourceId, int pending, String blogTitle, boolean fullContent) {
    ImportSourceEntity source = sources.findById(sourceId).orElse(null);
    if (source == null || !source.isVerified()) {
      return false;
    }
    source.refresh(blogTitle, fullContent);
    source.startRun(pending);
    return true;
  }

  @Transactional
  public boolean progress(Long sourceId, int done, int failed) {
    ImportSourceEntity source = sources.findById(sourceId).orElse(null);
    if (source == null || !source.isVerified()) {
      return false;
    }
    source.recordProgress(done, failed);
    return true;
  }

  @Transactional
  public void finish(
      Long sourceId, String blogTitle, boolean fullContent, FeedValidators validators) {
    sources
        .findById(sourceId)
        .ifPresent(
            source -> {
              Instant now = clock.instant();
              if (source.isVerified()) {
                source.refresh(blogTitle, fullContent);
              }
              source.finishRun(now);
              source.fetched(now);
              source.rememberValidators(validators);
            });
  }

  @Transactional
  public void notModified(Long sourceId) {
    sources
        .findById(sourceId)
        .ifPresent(
            source -> {
              Instant now = clock.instant();
              source.finishRun(now);
              source.fetched(now);
            });
  }

  @Transactional
  public void fail(Long sourceId, String errorCode) {
    sources
        .findById(sourceId)
        .ifPresent(
            source -> {
              source.failRun(errorCode);
              source.fetchFailed(clock.instant());
            });
  }
}
