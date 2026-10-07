package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.common.federation.ServerBlockSql;
import com.example.short_link.federation.domain.FederationDomainBlockEntity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FederationDomainBlockRepositoryAdapter implements FederationDomainBlockRepository {

  private static final String ON_SERVER = "(%1$s = :domain OR %1$s LIKE :subdomains)";

  private final JpaFederationDomainBlockRepository jpa;
  private final EntityManager em;

  @Override
  public List<FederationDomainBlockEntity> list() {
    return jpa.findAllByOrderByDomainAsc();
  }

  @Override
  public Optional<FederationDomainBlockEntity> find(String domain) {
    return jpa.findByDomain(domain);
  }

  @Override
  public boolean suspends(String host) {
    if (host == null || host.isBlank()) {
      return false;
    }
    return !em.createNativeQuery(
            "SELECT 1 FROM federation_domain_block b WHERE b.severity = 'SUSPEND' AND "
                + ServerBlockSql.covers("b", ":host")
                + " LIMIT 1")
        .setParameter("host", host.toLowerCase(Locale.ROOT))
        .getResultList()
        .isEmpty();
  }

  @Override
  public FederationDomainBlockEntity save(FederationDomainBlockEntity block) {
    return jpa.save(block);
  }

  @Override
  public int delete(String domain) {
    return jpa.deleteByDomainValue(domain);
  }

  @Override
  public void sever(String domain) {
    em.createNativeQuery(
            "DELETE f FROM federation_following f JOIN federation_remote_actor a"
                + " ON a.id = f.remote_actor_id WHERE "
                + ON_SERVER.formatted("a.domain"))
        .setParameter("domain", domain)
        .setParameter("subdomains", "%." + domain)
        .executeUpdate();
    em.createNativeQuery(
            "DELETE f FROM federation_follower f JOIN federation_remote_actor a"
                + " ON a.id = f.remote_actor_id WHERE "
                + ON_SERVER.formatted("a.domain"))
        .setParameter("domain", domain)
        .setParameter("subdomains", "%." + domain)
        .executeUpdate();
    em.createNativeQuery(
            "DELETE FROM federation_delivery WHERE status = 'PENDING' AND "
                + ON_SERVER.formatted("inbox_host"))
        .setParameter("domain", domain)
        .setParameter("subdomains", "%." + domain)
        .executeUpdate();
  }
}
