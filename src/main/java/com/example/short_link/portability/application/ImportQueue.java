package com.example.short_link.portability.application;

import com.example.short_link.portability.domain.repository.AccountImportRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The worker's own transactions: rows are taken in one that commits before they are applied (each
// application commits on its own), and the counts land in another.
@Component
@RequiredArgsConstructor
class ImportQueue {

  private final AccountImportRepository imports;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<AccountImportRepository.Claimed> claim(int limit, Instant now) {
    return imports.claim(limit, now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Long importId, int processed, int imported, Instant now) {
    imports.record(importId, processed, imported);
    imports.finishDone(now);
  }
}
