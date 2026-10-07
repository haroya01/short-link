package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.FederationDomainBlockEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFederationDomainBlockRepository
    extends JpaRepository<FederationDomainBlockEntity, Long> {

  List<FederationDomainBlockEntity> findAllByOrderByDomainAsc();

  Optional<FederationDomainBlockEntity> findByDomain(String domain);

  @Modifying
  @Query("delete from FederationDomainBlockEntity b where b.domain = :domain")
  int deleteByDomainValue(@Param("domain") String domain);
}
