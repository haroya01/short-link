package com.example.short_link.link.og.application;

import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LinkOgFetchListener {
  private final LinkOgFetchService fetchService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onLinkCreated(LinkOgFetchRequested event) {
    fetchService.fetchAfterCommit(event.shortCode(), event.originalUrl());
  }
}
