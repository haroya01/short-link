package com.example.short_link.portability.infrastructure.persistence;

import com.example.short_link.portability.domain.AccountImportEntity;
import com.example.short_link.portability.domain.AccountImportRowEntity;
import com.example.short_link.portability.domain.repository.AccountImportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AccountImportRepositoryAdapter implements AccountImportRepository {

  static final int INSERT_CHUNK = 500;

  private final JpaAccountImportRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public AccountImportEntity save(AccountImportEntity entity) {
    return jpa.save(entity);
  }

  // A file of thousands of lines is a handful of multi-row INSERTs, not one per line.
  @Override
  public void saveRows(List<AccountImportRowEntity> rows) {
    for (int from = 0; from < rows.size(); from += INSERT_CHUNK) {
      List<AccountImportRowEntity> chunk =
          rows.subList(from, Math.min(rows.size(), from + INSERT_CHUNK));
      StringBuilder sql =
          new StringBuilder("INSERT INTO account_import_row (import_id, fields) VALUES ");
      for (int i = 0; i < chunk.size(); i++) {
        sql.append(i == 0 ? "" : ", ")
            .append("(?")
            .append(2 * i + 1)
            .append(", ?")
            .append(2 * i + 2)
            .append(')');
      }
      Query insert = em.createNativeQuery(sql.toString());
      for (int i = 0; i < chunk.size(); i++) {
        insert.setParameter(2 * i + 1, chunk.get(i).getImportId());
        insert.setParameter(2 * i + 2, chunk.get(i).getFields());
      }
      insert.executeUpdate();
    }
  }

  @Override
  public boolean running(Long userId) {
    return jpa.existsByUserIdAndFinishedAtIsNull(userId);
  }

  @Override
  public List<AccountImportEntity> recent(Long userId, int limit) {
    return jpa.recent(userId, PageRequest.of(0, limit));
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Claimed> claim(int limit, Instant now) {
    List<Object> ids =
        em.createNativeQuery(
                "SELECT id FROM account_import_row WHERE processed_at IS NULL ORDER BY id"
                    + " LIMIT :limit FOR UPDATE SKIP LOCKED")
            .setParameter("limit", limit)
            .getResultList();
    if (ids.isEmpty()) {
      return List.of();
    }
    List<Long> rowIds = ids.stream().map(id -> ((Number) id).longValue()).toList();
    em.createNativeQuery("UPDATE account_import_row SET processed_at = :now WHERE id IN (:ids)")
        .setParameter("now", now)
        .setParameter("ids", rowIds)
        .executeUpdate();
    List<Object[]> rows =
        em.createQuery(
                "select r, i from AccountImportRowEntity r, AccountImportEntity i"
                    + " where i.id = r.importId and r.id in :ids order by r.id",
                Object[].class)
            .setParameter("ids", rowIds)
            .getResultList();
    return rows.stream()
        .map(row -> new Claimed((AccountImportRowEntity) row[0], (AccountImportEntity) row[1]))
        .toList();
  }

  @Override
  public void record(Long importId, int processed, int imported) {
    em.createNativeQuery(
            "UPDATE account_import SET processed_items = processed_items + :processed,"
                + " imported_items = imported_items + :imported WHERE id = :id")
        .setParameter("processed", processed)
        .setParameter("imported", imported)
        .setParameter("id", importId)
        .executeUpdate();
  }

  @Override
  public int finishDone(Instant now) {
    return em.createNativeQuery(
            "UPDATE account_import SET finished_at = :now"
                + " WHERE finished_at IS NULL AND processed_items >= total_items")
        .setParameter("now", now)
        .executeUpdate();
  }
}
