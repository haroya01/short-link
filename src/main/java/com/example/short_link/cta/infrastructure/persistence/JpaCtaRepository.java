package com.example.short_link.cta.infrastructure.persistence;

import com.example.short_link.cta.domain.CtaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCtaRepository extends JpaRepository<CtaEntity, Long> {

  List<CtaEntity> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
}
