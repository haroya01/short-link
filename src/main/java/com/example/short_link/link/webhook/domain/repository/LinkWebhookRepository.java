package com.example.short_link.link.webhook.domain.repository;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkWebhookRepository extends JpaRepository<LinkWebhookEntity, Long> {

  List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId);

  long countByLinkId(Long linkId);

  /**
   * Sweeps all enabled hooks subscribed to a delivery mode — the scheduler then filters in-memory
   * by timezone/hour rather than pushing that logic into SQL (the predicate set is small and the
   * join across users keeps the index-only path readable). One row per hook, so a scan over
   * ~thousands of hooks per tick is cheap relative to the HTTP fan-out that follows.
   */
  @Query(
      "SELECT h FROM LinkWebhookEntity h "
          + "WHERE h.enabled = true "
          + "AND (h.deliveryMode = :modeA OR h.deliveryMode = :modeB)")
  List<LinkWebhookEntity> findAllEnabledByDeliveryMode(
      @Param("modeA") WebhookDeliveryMode modeA, @Param("modeB") WebhookDeliveryMode modeB);
}
