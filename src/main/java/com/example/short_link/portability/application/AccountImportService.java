package com.example.short_link.portability.application;

import com.example.short_link.common.csv.CsvRows;
import com.example.short_link.portability.domain.AccountImportEntity;
import com.example.short_link.portability.domain.AccountImportRowEntity;
import com.example.short_link.portability.domain.ImportKind;
import com.example.short_link.portability.domain.repository.AccountImportRepository;
import com.example.short_link.portability.exception.PortabilityErrorCode;
import com.example.short_link.portability.exception.PortabilityException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Mastodon's import, merge mode: the lines of an export file are added to what the member already
// has (nothing is removed). The file is stored as lines and applied a batch at a time in the
// background, so a big follow list does not hold the request; the member watches the counts.
@Service
public class AccountImportService {

  static final int MAX_LINES = 5000;
  static final int MAX_LINE = 2048;
  static final int RECENT = 10;

  private final AccountImportRepository imports;
  private final ImportQueue queue;
  private final ImportRowApplier applier;
  private final Clock clock;

  @Autowired
  AccountImportService(
      AccountImportRepository imports, ImportQueue queue, ImportRowApplier applier) {
    this(imports, queue, applier, Clock.systemUTC());
  }

  AccountImportService(
      AccountImportRepository imports, ImportQueue queue, ImportRowApplier applier, Clock clock) {
    this.imports = imports;
    this.queue = queue;
    this.applier = applier;
    this.clock = clock;
  }

  public record ImportView(
      Long id,
      ImportKind kind,
      int total,
      int processed,
      int imported,
      boolean finished,
      Instant createdAt) {

    static ImportView of(AccountImportEntity e) {
      return new ImportView(
          e.getId(),
          e.getKind(),
          e.getTotalItems(),
          e.getProcessedItems(),
          e.getImportedItems(),
          e.isFinished(),
          e.getCreatedAt());
    }
  }

  @Transactional
  public ImportView start(Long userId, String kindName, String csv) {
    ImportKind kind =
        ImportKind.parse(kindName)
            .orElseThrow(
                () -> new PortabilityException(PortabilityErrorCode.IMPORT_KIND_UNKNOWN, kindName));
    if (imports.running(userId)) {
      throw new PortabilityException(PortabilityErrorCode.IMPORT_RUNNING);
    }
    List<List<String>> lines = CsvRows.parse(csv == null ? "" : csv);
    if (!lines.isEmpty()
        && kind.header() != null
        && kind.header().equalsIgnoreCase(lines.get(0).get(0))) {
      lines = lines.subList(1, lines.size());
    }
    lines =
        lines.stream()
            .filter(line -> !line.get(0).isEmpty())
            .filter(
                line -> String.join(AccountImportRowEntity.SEPARATOR, line).length() <= MAX_LINE)
            .toList();
    if (lines.isEmpty()) {
      throw new PortabilityException(PortabilityErrorCode.IMPORT_FILE_EMPTY);
    }
    if (lines.size() > MAX_LINES) {
      throw new PortabilityException(PortabilityErrorCode.IMPORT_FILE_TOO_LARGE, MAX_LINES);
    }
    AccountImportEntity saved =
        imports.save(new AccountImportEntity(userId, kind, lines.size(), clock.instant()));
    imports.saveRows(
        lines.stream().map(line -> new AccountImportRowEntity(saved.getId(), line)).toList());
    return ImportView.of(saved);
  }

  @Transactional(readOnly = true)
  public List<ImportView> recent(Long userId) {
    return imports.recent(userId, RECENT).stream().map(ImportView::of).toList();
  }

  // Applies up to `limit` waiting lines and returns how many were taken.
  public int processBatch(int limit) {
    Instant now = clock.instant();
    List<AccountImportRepository.Claimed> claimed = queue.claim(limit, now);
    Map<Long, int[]> counts = new LinkedHashMap<>();
    for (AccountImportRepository.Claimed line : claimed) {
      AccountImportEntity owner = line.owner();
      boolean applied = applier.apply(owner.getUserId(), owner.getKind(), line.row().cells());
      int[] count = counts.computeIfAbsent(owner.getId(), id -> new int[2]);
      count[0]++;
      if (applied) {
        count[1]++;
      }
    }
    counts.forEach((importId, count) -> queue.record(importId, count[0], count[1], now));
    return claimed.size();
  }
}
