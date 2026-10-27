package com.example.short_link.link.webhook.application.read;

import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.application.dto.WebhookSummary;
import com.example.short_link.link.webhook.domain.repository.LinkWebhookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkWebhookQueryService {

  private final LinkOwnership ownership;
  private final LinkWebhookRepository repository;

  public List<WebhookSummary> list(Long userId, ShortCode shortCode) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.linkId().value()).stream()
        .map(WebhookSummary::from)
        .toList();
  }
}
