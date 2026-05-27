package com.example.short_link.link.webhook.domain.repository;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import java.util.List;
import java.util.Optional;

public interface LinkWebhookRepository {

  Optional<LinkWebhookEntity> findById(Long id);

  LinkWebhookEntity save(LinkWebhookEntity webhook);

  void delete(LinkWebhookEntity webhook);

  List<LinkWebhookEntity> findAll();

  List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId);

  long countByLinkId(Long linkId);

  List<LinkWebhookEntity> findAllEnabledByDeliveryMode(
      WebhookDeliveryMode modeA, WebhookDeliveryMode modeB);
}
