package com.example.short_link.link.webhook.infrastructure.persistence;

import com.example.short_link.link.webhook.domain.LinkWebhookEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryMode;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkWebhookRepositoryAdapter implements LinkWebhookRepository {

  private final JpaLinkWebhookRepository jpa;

  @Override
  public Optional<LinkWebhookEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkWebhookEntity save(LinkWebhookEntity webhook) {
    return jpa.save(webhook);
  }

  @Override
  public void delete(LinkWebhookEntity webhook) {
    jpa.delete(webhook);
  }

  @Override
  public List<LinkWebhookEntity> findChunkOrderedById(Long afterId, int limit) {
    return jpa.findChunkOrderedById(afterId, PageRequest.ofSize(limit));
  }

  @Override
  public List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId) {
    return jpa.findAllByLinkIdOrderByIdAsc(linkId);
  }

  @Override
  public List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId) {
    return jpa.findAllByLinkIdAndEnabledTrue(linkId);
  }

  @Override
  public long countByLinkId(Long linkId) {
    return jpa.countByLinkId(linkId);
  }

  @Override
  public List<LinkWebhookEntity> findAllEnabledByDeliveryMode(
      WebhookDeliveryMode modeA, WebhookDeliveryMode modeB) {
    return jpa.findAllEnabledByDeliveryMode(modeA, modeB);
  }
}
