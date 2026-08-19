package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ClickFlusherTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-09T05:00:00Z");

  private final ClickEventAssembler assembler = mock(ClickEventAssembler.class);
  private final ClickEventRepository repository = mock(ClickEventRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ClickBuffer buffer = new ClickBuffer(new ClickRecorderProperties(100, 2));
  private final ClickFlusher flusher =
      new ClickFlusher(
          buffer,
          assembler,
          repository,
          events,
          registry,
          txManager,
          new ClickRecorderProperties(100, 2));

  @BeforeEach
  void stubTransaction() {
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
  }

  private PendingClick pending(long linkId) {
    return new PendingClick(
        ClickContext.of(new LinkId(linkId), "https://example.com", null, "ua", "1.2.3.4", null),
        null,
        OCCURRED_AT);
  }

  private ClickEventEntity entity(long linkId) {
    return ClickEventEntity.builder().linkId(new LinkId(linkId)).clickedAt(OCCURRED_AT).build();
  }

  @Test
  void flushAssemblesPersistsAndPublishesPerClick() {
    ClickEventEntity first = entity(1L);
    ClickEventEntity second = entity(2L);
    when(assembler.assemble(any())).thenReturn(first, second);
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    buffer.offer(pending(1L));
    buffer.offer(pending(2L));

    flusher.flush();

    assertThat(buffer.isEmpty()).isTrue();
    ArgumentCaptor<ClickRecordedEvent> captor = ArgumentCaptor.forClass(ClickRecordedEvent.class);
    verify(events, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
    assertThat(captor.getAllValues().get(0).linkId()).isEqualTo(new LinkId(1L));
    assertThat(registry.counter("click_recorder", "result", "flushed").count()).isEqualTo(2.0);
    // 발행이 트랜잭션 템플릿 안에서 돈다(getTransaction 2회 = persist 1 + publish 1). 밖에서
    // 발행하면 @TransactionalEventListener(AFTER_COMMIT) 소비자가 조용히 스킵된다(#656 회귀).
    verify(txManager, org.mockito.Mockito.times(2)).getTransaction(any());
  }

  @Test
  void flushIsNoOpWhenBufferEmpty() {
    flusher.flush();

    verify(repository, never()).saveAll(anyList());
  }

  @Test
  void respectsMaxBatchPerFlush() {
    when(assembler.assemble(any())).thenAnswer(inv -> entity(1L));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    buffer.offer(pending(1L));
    buffer.offer(pending(2L));
    buffer.offer(pending(3L));

    flusher.flush();

    assertThat(buffer.size()).isEqualTo(1);
  }

  @Test
  void unassemblableClickIsSkippedAloneAndCounted() {
    ClickEventEntity good = entity(2L);
    when(assembler.assemble(any())).thenThrow(new RuntimeException("bad ua")).thenReturn(good);
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    buffer.offer(pending(1L));
    buffer.offer(pending(2L));

    flusher.flush();

    ArgumentCaptor<List<ClickEventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(good);
    assertThat(registry.counter("click_recorder", "result", "assemble_error").count())
        .isEqualTo(1.0);
  }

  @Test
  void batchFailureRetriesIndividuallySoOnePoisonRowCannotDropTheRest() {
    ClickEventEntity poison = entity(1L);
    ClickEventEntity good = entity(2L);
    when(assembler.assemble(any())).thenReturn(poison, good);
    when(repository.saveAll(anyList())).thenThrow(new RuntimeException("batch failed"));
    when(repository.save(poison)).thenThrow(new RuntimeException("poison row"));
    when(repository.save(good)).thenReturn(good);
    buffer.offer(pending(1L));
    buffer.offer(pending(2L));

    assertThatNoException().isThrownBy(flusher::flush);

    verify(events).publishEvent(any(ClickRecordedEvent.class));
    assertThat(registry.counter("click_recorder", "result", "flushed").count()).isEqualTo(1.0);
    assertThat(registry.counter("click_recorder", "result", "flush_error").count()).isEqualTo(1.0);
  }

  @Test
  void drainOnShutdownFlushesUntilBufferEmpty() {
    when(assembler.assemble(any())).thenAnswer(inv -> entity(1L));
    when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    for (int i = 1; i <= 5; i++) {
      buffer.offer(pending(i));
    }

    flusher.drainOnShutdown();

    assertThat(buffer.isEmpty()).isTrue();
    assertThat(registry.counter("click_recorder", "result", "flushed").count()).isEqualTo(5.0);
  }
}
