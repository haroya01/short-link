package com.example.short_link.admin.domain.repository;

import com.example.short_link.admin.domain.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlockedDomainRepository extends JpaRepository<BlockedDomainEntity, Long> {

  Optional<BlockedDomainEntity> findByDomain(String domain);

  List<BlockedDomainEntity> findAllByOrderByBlockedAtDesc();

  @Query("SELECT b.domain FROM BlockedDomainEntity b")
  List<String> findAllDomains();
}
