package com.example.short_link.federation.infrastructure.event;

import com.example.short_link.common.event.NoteReportForwardRequested;
import com.example.short_link.federation.application.RemoteReports;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RemoteReportListener {

  private final RemoteReports reports;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onForwardRequested(NoteReportForwardRequested event) {
    reports.forward(event.noteId(), event.comment());
  }
}
