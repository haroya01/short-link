package com.example.short_link.link.stats.application;

import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository.LinkVisitor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClickRollupService {

  private final ClickRollupRepository rollups;
  private final ClickRetentionProperties properties;
  private final TransactionTemplate dayTransaction;
  private final TransactionTemplate deleteTransaction;

  public ClickRollupService(
      ClickRollupRepository rollups,
      ClickRetentionProperties properties,
      PlatformTransactionManager transactionManager) {
    this.rollups = rollups;
    this.properties = properties;
    this.dayTransaction = new TransactionTemplate(transactionManager);
    this.dayTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.dayTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.deleteTransaction = new TransactionTemplate(transactionManager);
    this.deleteTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  private Instant target(Instant now) {
    return now.minus(Duration.ofDays(properties.rawDays())).truncatedTo(ChronoUnit.DAYS);
  }

  public int rollUp(Instant now) {
    Instant target = target(now);
    int days = 0;
    Instant rolled = rollups.rolledUntil();
    while (rolled.isBefore(target)) {
      Optional<Instant> first = rollups.firstClickAtOrAfter(rolled);
      Instant dayStart =
          first
              .filter(t -> t.isBefore(target))
              .map(t -> t.truncatedTo(ChronoUnit.DAYS))
              .orElse(null);
      if (dayStart == null) {
        Instant until = target;
        dayTransaction.executeWithoutResult(status -> rollups.advanceTo(until));
        break;
      }
      Instant dayEnd = dayStart.plus(Duration.ofDays(1));
      dayTransaction.executeWithoutResult(
          status -> {
            rollups.rollUpDay(dayStart, dayEnd);
            visitorSketches(rollups.humanVisitors(dayStart, dayEnd))
                .forEach((linkId, sketch) -> rollups.saveVisitorSketch(linkId, dayStart, sketch));
            rollups.advanceTo(dayEnd);
          });
      rolled = dayEnd;
      days++;
    }
    return days;
  }

  public long deleteRolledUp() {
    Instant rolled = rollups.rolledUntil();
    long deleted = 0;
    while (true) {
      Integer batch =
          deleteTransaction.execute(
              status -> rollups.deleteRawBefore(rolled, properties.deleteBatch()));
      int removed = batch == null ? 0 : batch;
      deleted += removed;
      if (removed < properties.deleteBatch()) return deleted;
    }
  }

  private static Map<Long, byte[]> visitorSketches(List<LinkVisitor> visitors) {
    Map<Long, List<String>> byLink = new LinkedHashMap<>();
    for (LinkVisitor v : visitors) {
      byLink.computeIfAbsent(v.linkId(), id -> new ArrayList<>()).add(v.visitorHash());
    }
    Map<Long, byte[]> sketches = new LinkedHashMap<>();
    byLink.forEach((linkId, hashes) -> sketches.put(linkId, VisitorSketch.of(hashes)));
    return sketches;
  }
}
