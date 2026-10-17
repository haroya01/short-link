package com.example.short_link.common.image;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImageVariantSweep {

  static final int PAGE = 100;

  private final List<ImageVariantSource> sources;
  private final ImageVariantLedger ledger;
  private final ImageVariantQueue queue;
  private final ImageVariantProperties props;
  private final Deque<String> backlog = new ArrayDeque<>();
  private int source;
  private long cursor;
  private boolean finished;

  public ImageVariantSweep(
      List<ImageVariantSource> sources,
      ImageVariantLedger ledger,
      ImageVariantQueue queue,
      ImageVariantProperties props) {
    this.sources = sources;
    this.ledger = ledger;
    this.queue = queue;
    this.props = props;
  }

  @Scheduled(
      fixedDelayString = "${short-link.image-variants.sweep-delay:PT10M}",
      initialDelayString = "${short-link.image-variants.sweep-delay:PT10M}")
  public synchronized int sweepRecent() {
    if (!queue.enabled()) {
      return 0;
    }
    List<String> keys = new ArrayList<>();
    for (ImageVariantSource each : sources) {
      each.newest(props.recentRows()).stream()
          .filter(ImageVariantKeys::eligible)
          .forEach(keys::add);
    }
    int queued = 0;
    for (String key : ledger.unrecorded(keys)) {
      if (queue.enqueue(key)) {
        queued++;
      }
    }
    if (queued > 0) {
      log.info("image-variant sweep recent queued={}", queued);
    }
    return queued;
  }

  @Scheduled(fixedDelayString = "${short-link.image-variants.backfill-interval:PT1S}")
  public synchronized boolean backfillStep() {
    if (!props.backfillEnabled() || !queue.enabled() || finished || !queue.idle()) {
      return false;
    }
    if (backlog.isEmpty() && !readPage()) {
      return false;
    }
    String key = backlog.poll();
    if (key == null) {
      return false;
    }
    if (!queue.enqueue(key)) {
      backlog.addFirst(key);
      return false;
    }
    return true;
  }

  private boolean readPage() {
    if (source >= sources.size()) {
      finished = true;
      log.info("image-variant backfill finished");
      return false;
    }
    ImageVariantSource.Page page = sources.get(source).after(cursor, PAGE);
    if (page.cursor() <= cursor) {
      source++;
      cursor = 0;
      return false;
    }
    cursor = page.cursor();
    backlog.addAll(
        ledger.unrecorded(page.keys().stream().filter(ImageVariantKeys::eligible).toList()));
    return true;
  }
}
