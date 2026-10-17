package com.example.short_link.common.image;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class ImageVariantRequestListener {

  private final ImageVariantQueue queue;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  void onRequested(ImageVariantsRequested event) {
    event.keys().forEach(queue::enqueue);
  }
}
