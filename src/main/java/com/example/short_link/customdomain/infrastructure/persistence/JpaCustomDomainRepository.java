package com.example.short_link.customdomain.infrastructure.persistence;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCustomDomainRepository extends JpaRepository<CustomDomainEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from CustomDomainEntity d where d.id = :id")
  Optional<CustomDomainEntity> findByIdForUpdate(@Param("id") Long id);

  List<CustomDomainEntity> findAllByUserIdOrderByIdAsc(Long userId);

  Optional<CustomDomainEntity> findByDomain(String domain);

  boolean existsByDomain(String domain);

  List<CustomDomainEntity> findAllByVerifiedFalseAndCreatedAtAfter(Instant cutoff);
}
