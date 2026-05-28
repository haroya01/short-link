package com.example.short_link.cta.domain.repository;

import com.example.short_link.cta.domain.CtaEntity;
import java.util.List;
import java.util.Optional;

public interface CtaRepository {

  Optional<CtaEntity> findById(Long id);

  CtaEntity save(CtaEntity cta);

  List<CtaEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
}
