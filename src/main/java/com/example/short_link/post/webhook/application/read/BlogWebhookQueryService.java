package com.example.short_link.post.webhook.application.read;

import com.example.short_link.post.webhook.application.dto.BlogWebhookSummary;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogWebhookQueryService {

  private final BlogWebhookRepository repository;

  public List<BlogWebhookSummary> listForUser(Long userId) {
    return repository.findAllByUserId(userId).stream().map(BlogWebhookSummary::from).toList();
  }
}
