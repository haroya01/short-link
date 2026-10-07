package com.example.short_link.portability.domain.repository;

import com.example.short_link.portability.domain.AccountImportEntity;
import com.example.short_link.portability.domain.AccountImportRowEntity;
import java.time.Instant;
import java.util.List;

public interface AccountImportRepository {

  AccountImportEntity save(AccountImportEntity entity);

  void saveRows(List<AccountImportRowEntity> rows);

  boolean running(Long userId);

  List<AccountImportEntity> recent(Long userId, int limit);

  // The oldest unprocessed rows across imports, marked taken in the same transaction so another
  // worker skips them.
  List<Claimed> claim(int limit, Instant now);

  void record(Long importId, int processed, int imported);

  int finishDone(Instant now);

  record Claimed(AccountImportRowEntity row, AccountImportEntity owner) {}
}
