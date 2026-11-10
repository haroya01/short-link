package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.Optional;

public interface LinkEvidenceRepository {

  Optional<LinkFacts> findByShortCode(ShortCode shortCode);

  interface LinkFacts {
    Long getLinkId();

    String getOriginalUrl();

    String getExpiredRedirectUrl();

    Long getOwnerId();

    String getOgTitle();

    String getOgDescription();

    String getOgImage();

    String getOgFetchStatus();

    Instant getOgFetchedAt();

    Long getOwnerLinks();

    Long getOwnerDisabledLinks();
  }
}
