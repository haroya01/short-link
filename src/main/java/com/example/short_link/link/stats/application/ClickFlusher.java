package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.application.dto.RecordedClick;
import com.example.short_link.link.application.dto.SavedClick;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class ClickFlusher {

  private final ClickBuffer buffer;
  private final ClickEventAssembler assembler;
  private final ClickEventRepository repository;
  private final LinkRepository linkRepository;
  private final ApplicationEventPublisher events;
  private final ClickWebhookOutbox webhooks;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate transaction;
  private final int maxBatchPerFlush;

  public ClickFlusher(
      ClickBuffer buffer,
      ClickEventAssembler assembler,
      ClickEventRepository repository,
      LinkRepository linkRepository,
      ApplicationEventPublisher events,
      ClickWebhookOutbox webhooks,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager,
      ClickRecorderProperties properties) {
    this.buffer = buffer;
    this.assembler = assembler;
    this.repository = repository;
    this.linkRepository = linkRepository;
    this.events = events;
    this.webhooks = webhooks;
    this.meterRegistry = meterRegistry;
    // REQUIRED는 기존 트랜잭션에 참여해 인라인 flush도 호출자의 롤백에 포함한다.
    this.transaction = new TransactionTemplate(transactionManager);
    this.maxBatchPerFlush = properties.maxBatchPerFlush();
  }

  @Scheduled(fixedDelay = 1000)
  public void flush() {
    List<PendingClick> pending = buffer.drain(maxBatchPerFlush);
    if (pending.isEmpty()) {
      return;
    }
    List<Assembled> batch = assemble(pending);
    List<ClickEventEntity> saved = persist(batch);
    meterRegistry.counter("click_recorder", "result", "flushed").increment(saved.size());
    publishAll(saved);
  }

  // Publish inside a transaction so AFTER_COMMIT listeners run. On the scheduler path, persistence
  // has already committed, so a synchronous SSE listener failure cannot roll back saved clicks.
  private void publishAll(List<ClickEventEntity> saved) {
    if (saved.isEmpty()) {
      return;
    }
    Map<Long, LinkEntity> links =
        linkRepository
            .findAllById(saved.stream().map(e -> e.linkId().value()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(l -> l.linkId().value(), l -> l));
    transaction.executeWithoutResult(
        status -> saved.forEach(e -> publishRecorded(e, links.get(e.linkId().value()))));
  }

  // flush 는 실패해도 drain 으로 큐를 줄이므로 이 루프는 항상 끝난다.
  @PreDestroy
  void drainOnShutdown() {
    while (!buffer.isEmpty()) {
      flush();
    }
  }

  private List<Assembled> assemble(List<PendingClick> pending) {
    List<Assembled> batch = new ArrayList<>(pending.size());
    for (PendingClick click : pending) {
      try {
        batch.add(new Assembled(click, assembler.assemble(click)));
      } catch (RuntimeException e) {
        meterRegistry.counter("click_recorder", "result", "assemble_error").increment();
        log.warn("failed to assemble click for linkId={}", click.ctx().linkId(), e);
      }
    }
    return batch;
  }

  private List<ClickEventEntity> persist(List<Assembled> batch) {
    if (batch.isEmpty()) {
      return List.of();
    }
    try {
      return transaction.execute(
          status -> queued(repository.saveAll(batch.stream().map(Assembled::event).toList())));
    } catch (RuntimeException e) {
      log.warn("click batch insert failed, retrying {} rows individually", batch.size(), e);
      return persistIndividually(batch);
    }
  }

  private List<ClickEventEntity> persistIndividually(List<Assembled> batch) {
    List<ClickEventEntity> saved = new ArrayList<>(batch.size());
    for (Assembled assembled : batch) {
      try {
        saved.add(
            transaction.execute(
                status ->
                    queued(List.of(repository.save(assembler.assemble(assembled.click()))))
                        .getFirst()));
      } catch (RuntimeException e) {
        meterRegistry.counter("click_recorder", "result", "flush_error").increment();
        log.warn("dropped click for linkId={}: {}", assembled.event().getLinkId(), e.toString());
      }
    }
    return saved;
  }

  private List<ClickEventEntity> queued(List<ClickEventEntity> saved) {
    webhooks.enqueue(saved.stream().map(ClickFlusher::recorded).toList());
    return saved;
  }

  private static RecordedClick recorded(ClickEventEntity saved) {
    return new RecordedClick(
        saved.getId(),
        saved.linkId(),
        saved.getClickedAt(),
        saved.getCountryCode(),
        saved.getDeviceClass(),
        saved.getReferrerHost(),
        saved.isBot(),
        saved.getUtmSource());
  }

  private record Assembled(PendingClick click, ClickEventEntity event) {}

  private void publishRecorded(ClickEventEntity saved, LinkEntity link) {
    ClickRecordedEvent click =
        new ClickRecordedEvent(
            saved.linkId(),
            link != null ? link.getShortCode().value() : null,
            link != null ? link.getUserId() : null,
            saved.getClickedAt(),
            saved.getCountryCode(),
            saved.getDeviceClass(),
            saved.getReferrerHost(),
            saved.isBot(),
            saved.getUtmSource());
    events.publishEvent(click);
    events.publishEvent(new SavedClick(click, saved.getId()));
  }
}
