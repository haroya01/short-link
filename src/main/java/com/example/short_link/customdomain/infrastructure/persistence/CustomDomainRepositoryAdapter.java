package com.example.short_link.customdomain.infrastructure.persistence;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CustomDomainRepositoryAdapter implements CustomDomainRepository {

  private final JpaCustomDomainRepository jpa;

  @Override
  public Optional<CustomDomainEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public CustomDomainEntity save(CustomDomainEntity domain) {
    return jpa.save(domain);
  }

  @Override
  public void delete(CustomDomainEntity domain) {
    jpa.delete(domain);
  }

  @Override
  public List<CustomDomainEntity> findAllByUserIdOrderByIdAsc(Long userId) {
    return jpa.findAllByUserIdOrderByIdAsc(userId);
  }

  @Override
  public Optional<CustomDomainEntity> findByDomain(String domain) {
    return jpa.findByDomain(domain);
  }

  @Override
  public boolean existsByDomain(String domain) {
    return jpa.existsByDomain(domain);
  }

  @Override
  public List<CustomDomainEntity> findAllByVerifiedFalseAndCreatedAtAfter(Instant cutoff) {
    return jpa.findAllByVerifiedFalseAndCreatedAtAfter(cutoff);
  }
}
