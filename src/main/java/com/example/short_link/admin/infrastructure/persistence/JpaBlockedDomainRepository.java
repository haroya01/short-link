package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaBlockedDomainRepository extends JpaRepository<BlockedDomainEntity, Long> {

  Optional<BlockedDomainEntity> findByDomain(String domain);

  List<BlockedDomainEntity> findAllByOrderByBlockedAtDesc();

  @Query("SELECT b.domain FROM BlockedDomainEntity b")
  List<String> findAllDomains();
}
