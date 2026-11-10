package com.example.short_link.link.webhook.domain.repository;

import com.example.short_link.link.webhook.domain.LinkWebhookDeliveryEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LinkWebhookDeliveryRepository {

  List<LinkWebhookDeliveryEntity> saveAll(Collection<LinkWebhookDeliveryEntity> deliveries);

  List<LinkWebhookDeliveryEntity> findAllById(Collection<Long> ids);

  List<LinkWebhookDeliveryEntity> lockDue(Instant now, int limit);

  int deleteFinishedBefore(Instant cutoff);
}
