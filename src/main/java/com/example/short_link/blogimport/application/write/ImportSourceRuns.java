package com.example.short_link.blogimport.application.write;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import com.example.short_link.blogimport.domain.repository.ImportSourceRepository;
import java.time.Clock;
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
  public void finish(Long sourceId, String blogTitle, boolean fullContent) {
    sources
        .findById(sourceId)
        .ifPresent(
            source -> {
              if (source.isVerified()) {
                source.refresh(blogTitle, fullContent);
              }
              source.finishRun(clock.instant());
            });
  }

  @Transactional
  public void fail(Long sourceId, String errorCode) {
    sources.findById(sourceId).ifPresent(source -> source.failRun(errorCode));
  }
}
