package com.example.short_link.portability.infrastructure.persistence;

import com.example.short_link.portability.application.ImportLookup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class ImportLookupAdapter implements ImportLookup {

  @PersistenceContext private EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public Optional<Long> noteIdByUri(String uri) {
    List<Object> ids =
        em.createNativeQuery("SELECT id FROM note WHERE uri = :uri OR remote_url = :uri LIMIT 1")
            .setParameter("uri", uri)
            .getResultList();
    return ids.stream().findFirst().map(id -> ((Number) id).longValue());
  }
}
