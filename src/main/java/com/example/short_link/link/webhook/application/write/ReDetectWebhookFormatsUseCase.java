package com.example.short_link.link.webhook.application.write;

import com.example.short_link.link.webhook.application.dto.WebhookReDetectResult;
import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReDetectWebhookFormatsUseCase {

  private static final int CHUNK_SIZE = 500;

  private final LinkWebhookRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public WebhookReDetectResult execute() {
    int scanned = 0;
    int formatChanged = 0;
    int reactivated = 0;
    long afterId = 0L;
    while (true) {
      List<LinkWebhookEntity> chunk = repository.findChunkOrderedById(afterId, CHUNK_SIZE);
      if (chunk.isEmpty()) break;
      for (LinkWebhookEntity hook : chunk) {
        scanned++;
        LinkWebhookEntity.FormatRedetection result = hook.redetectFormat();
        if (result.changed()) formatChanged++;
        if (result.reactivated()) reactivated++;
      }
      afterId = chunk.get(chunk.size() - 1).getId();
      if (chunk.size() < CHUNK_SIZE) break;
    }
    meterRegistry.counter("link.webhook.redetect", "result", "scanned").increment(scanned);
    if (formatChanged > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "format_changed")
          .increment(formatChanged);
    }
    if (reactivated > 0) {
      meterRegistry
          .counter("link.webhook.redetect", "result", "reactivated")
          .increment(reactivated);
    }
    return new WebhookReDetectResult(scanned, formatChanged, reactivated);
  }
}
