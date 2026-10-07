package com.example.short_link.portability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.portability.domain.AccountImportEntity;
import com.example.short_link.portability.domain.AccountImportRowEntity;
import com.example.short_link.portability.domain.ImportKind;
import com.example.short_link.portability.domain.repository.AccountImportRepository;
import com.example.short_link.portability.exception.PortabilityErrorCode;
import com.example.short_link.portability.exception.PortabilityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountImportServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

  @Mock private AccountImportRepository imports;
  @Mock private ImportQueue queue;
  @Mock private ImportRowApplier applier;

  private AccountImportService service() {
    return new AccountImportService(imports, queue, applier, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static AccountImportEntity saved(Long id, ImportKind kind, int total) {
    AccountImportEntity e = new AccountImportEntity(7L, kind, total, NOW);
    ReflectionTestUtils.setField(e, "id", id);
    return e;
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFollowingFileDropsItsHeaderAndStoresALineEach() {
    when(imports.save(any())).thenAnswer(inv -> saved(3L, ImportKind.FOLLOWING, 2));

    AccountImportService.ImportView view =
        service()
            .start(
                7L,
                "following",
                "Account address,Show boosts,Notify on new posts,Languages\nsori@kurl.me,true,false,\nmio,false,true,\n");

    assertThat(view.total()).isEqualTo(2);
    assertThat(view.finished()).isFalse();
    ArgumentCaptor<List<AccountImportRowEntity>> rows = ArgumentCaptor.forClass(List.class);
    verify(imports).saveRows(rows.capture());
    assertThat(rows.getValue())
        .extracting(AccountImportRowEntity::cells)
        .containsExactly(
            List.of("sori@kurl.me", "true", "false", ""), List.of("mio", "false", "true", ""));
    ArgumentCaptor<AccountImportEntity> entity = ArgumentCaptor.forClass(AccountImportEntity.class);
    verify(imports).save(entity.capture());
    assertThat(entity.getValue().getTotalItems()).isEqualTo(2);
  }

  @Test
  void aFileWithoutHeaderKeepsItsFirstLine() {
    when(imports.save(any())).thenAnswer(inv -> saved(3L, ImportKind.DOMAIN_BLOCKS, 2));

    service().start(7L, "domain-blocks", "spam.example\nother.example");

    ArgumentCaptor<AccountImportEntity> entity = ArgumentCaptor.forClass(AccountImportEntity.class);
    verify(imports).save(entity.capture());
    assertThat(entity.getValue().getTotalItems()).isEqualTo(2);
    assertThat(entity.getValue().getKind()).isEqualTo(ImportKind.DOMAIN_BLOCKS);
  }

  @Test
  void anUnknownKindARunningImportAndAnEmptyOrHugeFileAreTurnedAway() {
    AccountImportService service = service();
    assertThatThrownBy(() -> service.start(7L, "passwords", "x"))
        .isInstanceOfSatisfying(
            PortabilityException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PortabilityErrorCode.IMPORT_KIND_UNKNOWN));
    assertThatThrownBy(() -> service.start(7L, null, "x")).isInstanceOf(PortabilityException.class);

    when(imports.running(7L)).thenReturn(true);
    assertThatThrownBy(() -> service.start(7L, "blocks", "a"))
        .isInstanceOfSatisfying(
            PortabilityException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PortabilityErrorCode.IMPORT_RUNNING));

    when(imports.running(7L)).thenReturn(false);
    assertThatThrownBy(() -> service.start(7L, "following", "Account address,Show boosts\n"))
        .isInstanceOfSatisfying(
            PortabilityException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PortabilityErrorCode.IMPORT_FILE_EMPTY));
    assertThatThrownBy(() -> service.start(7L, "blocks", null))
        .isInstanceOf(PortabilityException.class);
    String huge = "a\n".repeat(AccountImportService.MAX_LINES + 1);
    assertThatThrownBy(() -> service.start(7L, "blocks", huge))
        .isInstanceOfSatisfying(
            PortabilityException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PortabilityErrorCode.IMPORT_FILE_TOO_LARGE));
    verify(imports, never()).save(any());
  }

  @Test
  void aLineTooLongToStoreIsLeftOut() {
    when(imports.save(any())).thenAnswer(inv -> saved(3L, ImportKind.BLOCKS, 1));

    service().start(7L, "blocks", "sori\n" + "x".repeat(AccountImportService.MAX_LINE + 1));

    ArgumentCaptor<AccountImportEntity> entity = ArgumentCaptor.forClass(AccountImportEntity.class);
    verify(imports).save(entity.capture());
    assertThat(entity.getValue().getTotalItems()).isEqualTo(1);
  }

  @Test
  void aBatchAppliesEachLineAndCountsPerImport() {
    AccountImportEntity follows = saved(3L, ImportKind.FOLLOWING, 2);
    AccountImportEntity servers = saved(4L, ImportKind.DOMAIN_BLOCKS, 1);
    AccountImportRowEntity a = new AccountImportRowEntity(3L, List.of("sori"));
    AccountImportRowEntity b = new AccountImportRowEntity(3L, List.of("ghost"));
    AccountImportRowEntity c = new AccountImportRowEntity(4L, List.of("spam.example"));
    when(queue.claim(50, NOW))
        .thenReturn(
            List.of(
                new AccountImportRepository.Claimed(a, follows),
                new AccountImportRepository.Claimed(b, follows),
                new AccountImportRepository.Claimed(c, servers)));
    when(applier.apply(7L, ImportKind.FOLLOWING, List.of("sori"))).thenReturn(true);
    when(applier.apply(7L, ImportKind.FOLLOWING, List.of("ghost"))).thenReturn(false);
    when(applier.apply(7L, ImportKind.DOMAIN_BLOCKS, List.of("spam.example"))).thenReturn(true);

    assertThat(service().processBatch(50)).isEqualTo(3);

    verify(queue).record(3L, 2, 1, NOW);
    verify(queue).record(4L, 1, 1, NOW);
  }

  @Test
  void recentImportsShowTheirCounts() {
    AccountImportEntity done = saved(3L, ImportKind.BLOCKS, 2);
    ReflectionTestUtils.setField(done, "processedItems", 2);
    ReflectionTestUtils.setField(done, "importedItems", 1);
    ReflectionTestUtils.setField(done, "finishedAt", NOW);
    when(imports.recent(7L, AccountImportService.RECENT)).thenReturn(List.of(done));

    assertThat(service().recent(7L))
        .containsExactly(
            new AccountImportService.ImportView(3L, ImportKind.BLOCKS, 2, 2, 1, true, NOW));
  }
}
