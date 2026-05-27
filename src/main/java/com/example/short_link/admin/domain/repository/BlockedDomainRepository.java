package com.example.short_link.admin.domain.repository;

import com.example.short_link.admin.domain.*;
import java.util.List;
import java.util.Optional;

public interface BlockedDomainRepository {

  BlockedDomainEntity save(BlockedDomainEntity domain);

  void delete(BlockedDomainEntity domain);

  Optional<BlockedDomainEntity> findByDomain(String domain);

  List<BlockedDomainEntity> findAllByOrderByBlockedAtDesc();

  List<String> findAllDomains();
}
