package com.example.short_link.link.og.infrastructure.persistence;

import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface JpaLinkOgMetadataRepository
    extends JpaRepository<LinkOgMetadataEntity, Long>, LinkOgMetadataRepository {

  @Override
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE LinkOgMetadataEntity m SET m.ogFetchStatus = 'RETRYABLE', m.ogFetchAttempts = 0 "
          + "WHERE m.ogFetchStatus = 'ERROR'")
  int reopenGivenUpFetches();
}
