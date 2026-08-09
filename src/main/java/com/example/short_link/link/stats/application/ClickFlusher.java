package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains the {@link ClickBuffer} into click_event on a schedule, off the request thread. A redirect
 * never waits on this — recording stays best-effort exactly as before, but a slow or exhausted DB
 * now costs analytics rows instead of 302s.
 *
 * <p>Failure policy: an unassemblable click is skipped alone; a failed batch insert retries each
 * row individually so one poison row cannot take down the other clicks in the batch. Every drop is
 * counted — evidence over silence.
 */
@Slf4j
@Component
public class ClickFlusher {

  private final ClickBuffer buffer;
  private final ClickEventAssembler assembler;
  private final ClickEventRepository repository;
  private final ApplicationEventPublisher events;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate transaction;
  private final int maxBatchPerFlush;

  public ClickFlusher(
      ClickBuffer buffer,
      ClickEventAssembler assembler,
      ClickEventRepository repository,
      ApplicationEventPublisher events,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager,
      ClickRecorderProperties properties) {
    this.buffer = buffer;
    this.assembler = assembler;
    this.repository = repository;
    this.events = events;
    this.meterRegistry = meterRegistry;
    // REQUIRED, not REQUIRES_NEW: the scheduler thread has no enclosing transaction so a new one
    // starts anyway, while @Transactional tests keep their rollback when they flush in-line.
    this.transaction = new TransactionTemplate(transactionManager);
    this.maxBatchPerFlush = properties.maxBatchPerFlush();
  }

  @Scheduled(fixedDelay = 1000)
  public void flush() {
    List<PendingClick> pending = buffer.drain(maxBatchPerFlush);
    if (pending.isEmpty()) {
      return;
    }
    List<ClickEventEntity> batch = assemble(pending);
    List<ClickEventEntity> saved = persist(batch);
    meterRegistry.counter("click_recorder", "result", "flushed").increment(saved.size());
    saved.forEach(this::publishRecorded);
  }

  /** 계획 셧다운은 무손실 — 버퍼가 빌 때까지 밀어낸다. 실패 시 drain 이 항상 큐를 줄이므로 종료는 보장된다. */
  @PreDestroy
  void drainOnShutdown() {
    while (!buffer.isEmpty()) {
      flush();
    }
  }

  private List<ClickEventEntity> assemble(List<PendingClick> pending) {
    List<ClickEventEntity> batch = new ArrayList<>(pending.size());
    for (PendingClick click : pending) {
      try {
        batch.add(assembler.assemble(click));
      } catch (RuntimeException e) {
        meterRegistry.counter("click_recorder", "result", "assemble_error").increment();
        log.warn("failed to assemble click for linkId={}", click.ctx().linkId(), e);
      }
    }
    return batch;
  }

  private List<ClickEventEntity> persist(List<ClickEventEntity> batch) {
    if (batch.isEmpty()) {
      return batch;
    }
    try {
      return transaction.execute(status -> repository.saveAll(batch));
    } catch (RuntimeException e) {
      log.warn("click batch insert failed, retrying {} rows individually", batch.size(), e);
      return persistIndividually(batch);
    }
  }

  private List<ClickEventEntity> persistIndividually(List<ClickEventEntity> batch) {
    List<ClickEventEntity> saved = new ArrayList<>(batch.size());
    for (ClickEventEntity entity : batch) {
      try {
        saved.add(transaction.execute(status -> repository.save(entity)));
      } catch (RuntimeException e) {
        meterRegistry.counter("click_recorder", "result", "flush_error").increment();
        log.warn("dropped click for linkId={}: {}", entity.getLinkId(), e.toString());
      }
    }
    return saved;
  }

  private void publishRecorded(ClickEventEntity saved) {
    events.publishEvent(
        new ClickRecordedEvent(
            saved.linkId(),
            saved.getClickedAt(),
            saved.getCountryCode(),
            saved.getDeviceClass(),
            saved.getReferrerHost(),
            saved.isBot(),
            saved.getUtmSource()));
  }
}
