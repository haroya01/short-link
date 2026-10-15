package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.ImportedPostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
class ImportedPostRepositoryAdapter implements ImportedPostRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public List<Long> findIdsByImportSourceId(Long importSourceId) {
    return em.createQuery(
            "select p.id from PostEntity p where p.importSourceId = :sourceId order by p.id",
            Long.class)
        .setParameter("sourceId", importSourceId)
        .getResultList();
  }

  @Override
  public Map<Long, Long> countPublishedByImportSourceIds(Collection<Long> importSourceIds) {
    Map<Long, Long> counts = new HashMap<>();
    if (importSourceIds.isEmpty()) {
      return counts;
    }
    List<Object[]> rows =
        em.createQuery(
                "select p.importSourceId, count(p) from PostEntity p"
                    + " where p.importSourceId in :sourceIds"
                    + " and p.status = com.example.short_link.post.domain.PostStatus.PUBLISHED"
                    + " group by p.importSourceId",
                Object[].class)
            .setParameter("sourceIds", importSourceIds)
            .getResultList();
    for (Object[] row : rows) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  @Override
  public int updateHiddenFromDiscover(Long importSourceId, boolean hidden) {
    return em.createQuery(
            "update PostEntity p set p.hiddenFromDiscover = :hidden"
                + " where p.importSourceId = :sourceId")
        .setParameter("hidden", hidden)
        .setParameter("sourceId", importSourceId)
        .executeUpdate();
  }
}
