package com.example.short_link.blogimport.scheduler;

import com.example.short_link.blogimport.application.write.SyncImportSourcesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlogImportSyncJob {

  private final SyncImportSourcesUseCase sync;

  @Scheduled(cron = "${short-link.blog-import.sync-cron:0 */5 * * * *}", zone = "Asia/Seoul")
  public void tick() {
    sync.execute();
  }
}
