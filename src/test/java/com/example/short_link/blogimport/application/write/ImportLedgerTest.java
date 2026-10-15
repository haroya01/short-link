package com.example.short_link.blogimport.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.domain.ImportItemEntity;
import com.example.short_link.blogimport.domain.repository.ImportItemRepository;
import com.example.short_link.blogimport.infrastructure.event.FirstImportListener;
import com.example.short_link.blogimport.scheduler.BlogImportSyncJob;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImportLedgerTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

  private final ImportItemRepository items = mock(ImportItemRepository.class);
  private final ImportLedger ledger = new ImportLedger(items, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void takenItemsAreKeyedByTheirHash() {
    ImportItemEntity item = new ImportItemEntity(1L, "h1");
    when(items.findBySourceIdAndUrlHashIn(1L, Set.of("h1", "h2"))).thenReturn(List.of(item));

    assertThat(ledger.taken(1L, Set.of("h1", "h2"))).containsOnlyKeys("h1");
  }

  @Test
  void claimingWritesARowAndOutcomesUpdateIt() {
    ImportItemEntity saved = new ImportItemEntity(1L, "h1");
    ReflectionTestUtils.setField(saved, "id", 5L);
    when(items.saveAndFlush(any())).thenReturn(saved);
    when(items.findById(5L)).thenReturn(Optional.of(saved));
    when(items.findById(6L)).thenReturn(Optional.empty());

    assertThat(ledger.claim(1L, "h1")).isEqualTo(5L);
    ledger.attach(5L, 40L);
    ledger.fail(5L);
    ledger.complete(5L);
    ledger.fail(6L);

    assertThat(saved.getPostId()).isEqualTo(40L);
    assertThat(saved.getAttempts()).isEqualTo(1);
    assertThat(saved.getImportedAt()).isEqualTo(NOW);
  }

  @Test
  void theListenerAndTheJobHandTheWorkToTheRunners() {
    BlogImportRunner runner = mock(BlogImportRunner.class);
    new FirstImportListener(runner).onVerified(new ImportSourceVerified(4L));
    verify(runner).run(4L);

    SyncImportSourcesUseCase sync = mock(SyncImportSourcesUseCase.class);
    new BlogImportSyncJob(sync).tick();
    verify(sync).execute();
  }
}
