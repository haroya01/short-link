package com.example.short_link.cta.infrastructure.persistence;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CtaRepositoryAdapter implements CtaRepository {

  private final JpaCtaRepository jpa;

  @Override
  public Optional<CtaEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public CtaEntity save(CtaEntity cta) {
    return jpa.save(cta);
  }

  @Override
  public List<CtaEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
  }
}
