package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.domain.repository.BlockedDomainRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JpaBlockedDomainRepository
    extends JpaRepository<BlockedDomainEntity, Long>, BlockedDomainRepository {

  @Override
  @Query("SELECT b.domain FROM BlockedDomainEntity b")
  List<String> findAllDomains();
}
