package com.example.short_link.link.visit.domain.repository;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import java.util.List;
import java.util.Optional;

public interface LinkVisitOptionRepository {

  Optional<LinkVisitOptionEntity> findById(Long linkId);

  LinkVisitOptionEntity save(LinkVisitOptionEntity option);

  List<ShortCode> findShortCodesUsingSplashCta(Long ctaId);
}
