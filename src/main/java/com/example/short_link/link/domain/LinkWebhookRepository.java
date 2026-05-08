package com.example.short_link.link.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkWebhookRepository extends JpaRepository<LinkWebhookEntity, Long> {

  List<LinkWebhookEntity> findAllByLinkIdOrderByIdAsc(Long linkId);

  List<LinkWebhookEntity> findAllByLinkIdAndEnabledTrue(Long linkId);

  long countByLinkId(Long linkId);
}
