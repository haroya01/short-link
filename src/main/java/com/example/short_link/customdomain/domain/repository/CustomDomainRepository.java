package com.example.short_link.customdomain.domain.repository;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomDomainRepository extends JpaRepository<CustomDomainEntity, Long> {

  List<CustomDomainEntity> findAllByUserIdOrderByIdAsc(Long userId);

  Optional<CustomDomainEntity> findByDomain(String domain);

  boolean existsByDomain(String domain);

  List<CustomDomainEntity> findAllByVerifiedFalseAndCreatedAtAfter(Instant cutoff);
}
