package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkEvidenceRepository.LinkFacts;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface JpaLinkEvidenceQueries extends Repository<LinkEntity, Long> {

  @Query(
      "SELECT l.id AS linkId, l.originalUrl AS originalUrl, "
          + "l.expiredRedirectUrl AS expiredRedirectUrl, l.userId AS ownerId, "
          + "l.ogTitle AS ogTitle, l.ogDescription AS ogDescription, l.ogImage AS ogImage, "
          + "l.ogFetchStatus AS ogFetchStatus, l.ogFetchedAt AS ogFetchedAt, "
          + "(SELECT COUNT(o) FROM LinkEntity o WHERE o.userId = l.userId) AS ownerLinks, "
          + "(SELECT COUNT(m) FROM LinkModerationEntity m JOIN LinkEntity d ON d.id = m.linkId "
          + "WHERE d.userId = l.userId) AS ownerDisabledLinks "
          + "FROM LinkEntity l WHERE l.shortCode = :shortCode")
  Optional<LinkFacts> findFactsByShortCode(@Param("shortCode") ShortCode shortCode);
}
