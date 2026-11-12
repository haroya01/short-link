package com.example.short_link.link.webhook.infrastructure.persistence;

import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import com.example.short_link.link.webhook.domain.WebhookDeliveryStatus;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookDeliveryRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkWebhookDeliveryRepositoryAdapter implements LinkWebhookDeliveryRepository {

  private final JpaLinkWebhookDeliveryRepository jpa;

  @Override
  public List<LinkWebhookDeliveryEntity> saveAll(Collection<LinkWebhookDeliveryEntity> deliveries) {
    return jpa.saveAll(deliveries);
  }

  @Override
  public List<LinkWebhookDeliveryEntity> findAllById(Collection<Long> ids) {
    return jpa.findAllById(ids);
  }

  @Override
  public List<LinkWebhookDeliveryEntity> lockDue(Instant now, int limit) {
    return jpa.lockDue(WebhookDeliveryStatus.PENDING, now, PageRequest.of(0, limit));
  }

  @Override
  public int deleteFinishedBefore(Instant cutoff) {
    return jpa.deleteFinishedBefore(WebhookDeliveryStatus.PENDING, cutoff);
  }
}
