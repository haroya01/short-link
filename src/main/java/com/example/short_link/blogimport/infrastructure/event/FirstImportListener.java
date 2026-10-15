package com.example.short_link.blogimport.infrastructure.event;

import com.example.short_link.blogimport.application.write.BlogImportRunner;
import com.example.short_link.blogimport.application.write.ImportSourceVerified;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FirstImportListener {

  private final BlogImportRunner runner;

  @Async("blogImportExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVerified(ImportSourceVerified event) {
    runner.run(event.sourceId());
  }
}
