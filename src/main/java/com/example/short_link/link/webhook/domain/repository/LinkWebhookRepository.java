package com.example.short_link.link.webhook.domain.repository;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LinkWebhookRepository {

  Optional<LinkWebhookEntity> findById(Long id);

  LinkWebhookEntity save(LinkWebhookEntity webhook);

  void delete(LinkWebhookEntity webhook);

  List<LinkWebhookEntity> findChunkOrderedById(Long afterId, int limit);

  List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdInAndEnabledTrue(Collection<Long> linkIds);

  long countByLinkId(Long linkId);

  List<LinkWebhookEntity> findAllEnabledByDeliveryModes(Collection<WebhookDeliveryMode> modes);

  boolean claimSpikeAlert(Long id, Instant firedAt, Instant lastFiredNoLaterThan);
}
