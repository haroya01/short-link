package com.example.short_link.admin.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedDomainRepository extends JpaRepository<BlockedDomainEntity, Long> {

  Optional<BlockedDomainEntity> findByDomain(String domain);

  List<BlockedDomainEntity> findAllByOrderByBlockedAtDesc();

  boolean existsByDomain(String domain);
}
