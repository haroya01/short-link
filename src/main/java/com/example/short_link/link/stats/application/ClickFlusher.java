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
    // REQUIRED(기본값)여야 한다: 스케줄러 스레드에선 어차피 새 트랜잭션이고, @Transactional 테스트는
    // 인라인 플러시를 롤백에 포함시킬 수 있다. REQUIRES_NEW 로 바꾸면 테스트가 DB 를 오염시킨다.
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
    publishAll(saved);
  }

  /**
   * ClickRecordedEvent 발행을 트랜잭션 경계 안에서 한다. 클릭 비동기화(#656) 이후 이 플러셔가 트랜잭션 밖 스케줄러 스레드에서 발행하면서, 소비자
   * 3종(웹훅 디스패처·스파이크 감지·클릭 푸시)이 전부 @TransactionalEventListener(AFTER_COMMIT) 라 조용히 스킵됐다 — 커밋 시점에
   * 바인딩된 트랜잭션이 없었기 때문. persist 와 분리된 별도(빈) 트랜잭션이라, 동기 @EventListener(SSE)가 발행 중 던져도 이미 저장된 클릭을 되돌리지
   * 않는다. AFTER_COMMIT 리스너는 이 트랜잭션 커밋 직후 webhookExecutor 로 디스패치된다.
   */
  private void publishAll(List<ClickEventEntity> saved) {
    if (saved.isEmpty()) {
      return;
    }
    transaction.executeWithoutResult(status -> saved.forEach(this::publishRecorded));
  }

  // flush 는 실패해도 drain 으로 큐를 줄이므로 이 루프는 항상 끝난다.
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
