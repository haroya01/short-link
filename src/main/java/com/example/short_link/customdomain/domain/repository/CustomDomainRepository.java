package com.example.short_link.customdomain.domain.repository;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomDomainRepository {

  Optional<CustomDomainEntity> findById(Long id);

  CustomDomainEntity save(CustomDomainEntity domain);

  void delete(CustomDomainEntity domain);

  List<CustomDomainEntity> findAllByUserIdOrderByIdAsc(Long userId);

  Optional<CustomDomainEntity> findByDomain(String domain);

  boolean existsByDomain(String domain);

  List<CustomDomainEntity> findAllByVerifiedFalseAndCreatedAtAfter(Instant cutoff);
}
