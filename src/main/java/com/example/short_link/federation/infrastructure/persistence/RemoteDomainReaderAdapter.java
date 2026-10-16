package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.federation.domain.repository.RemoteDomainReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteDomainReaderAdapter implements RemoteDomainReader {

  private final EntityManager em;

  @Override
  @SuppressWarnings("unchecked")
  public List<KnownDomain> known(Collection<String> named, int cap) {
    List<String> names = List.copyOf(named);
    StringBuilder rows =
        new StringBuilder(
            "(SELECT a.domain AS domain, COUNT(*) AS accounts FROM federation_remote_actor a"
                + " GROUP BY a.domain ORDER BY accounts DESC, a.domain LIMIT :cap)");
    for (int i = 0; i < names.size(); i++) {
      rows.append(" UNION ALL SELECT :named").append(i).append(", 0");
    }
    Query query =
        em.createNativeQuery(
                "SELECT k.domain, MAX(k.accounts) FROM ("
                    + rows
                    + ") k WHERE NOT "
                    + ServerBlockSql.blocks("k.domain")
                    + " GROUP BY k.domain")
            .setParameter("cap", cap);
    for (int i = 0; i < names.size(); i++) {
      query.setParameter("named" + i, names.get(i));
    }
    List<KnownDomain> known = new ArrayList<>();
    for (Object[] row : (List<Object[]>) query.getResultList()) {
      known.add(new KnownDomain((String) row[0], ((Number) row[1]).longValue()));
    }
    return known;
  }
}
