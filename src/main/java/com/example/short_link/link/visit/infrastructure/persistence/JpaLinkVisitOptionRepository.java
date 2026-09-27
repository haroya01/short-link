package com.example.short_link.link.visit.infrastructure.persistence;

import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkVisitOptionRepository extends JpaRepository<LinkVisitOptionEntity, Long> {

  @Query(
      value =
          "SELECT l.short_code FROM link_visit_option v JOIN link l ON l.id = v.link_id"
              + " WHERE v.splash_cta_id = :ctaId",
      nativeQuery = true)
  List<String> findShortCodesUsingSplashCta(@Param("ctaId") Long ctaId);
}
