package com.example.short_link.link.webhook.infrastructure.persistence;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaLinkWebhookRepository extends JpaRepository<LinkWebhookEntity, Long> {

  @Query("SELECT h FROM LinkWebhookEntity h WHERE h.id > :afterId ORDER BY h.id ASC")
  List<LinkWebhookEntity> findChunkOrderedById(@Param("afterId") Long afterId, Pageable pageable);

  List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdInAndEnabledTrue(Collection<Long> linkIds);

  long countByLinkId(Long linkId);

  @Query(
      "SELECT h FROM LinkWebhookEntity h "
          + "WHERE h.enabled = true "
          + "AND h.deliveryMode IN :modes")
  List<LinkWebhookEntity> findAllEnabledByDeliveryModes(
      @Param("modes") Collection<WebhookDeliveryMode> modes);

  @Modifying
  @Query(
      "UPDATE LinkWebhookEntity h SET h.spikeLastFiredAt = :firedAt "
          + "WHERE h.id = :id "
          + "AND (h.spikeLastFiredAt IS NULL OR h.spikeLastFiredAt <= :lastFiredNoLaterThan)")
  int claimSpikeAlert(
      @Param("id") Long id,
      @Param("firedAt") Instant firedAt,
      @Param("lastFiredNoLaterThan") Instant lastFiredNoLaterThan);
}
