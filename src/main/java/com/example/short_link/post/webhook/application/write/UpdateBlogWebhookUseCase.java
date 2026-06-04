package com.example.short_link.post.webhook.application.write;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.application.dto.BlogWebhookSummary;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits a blog webhook's name, the interactions it fires on, and its enabled state. */
@Service
@RequiredArgsConstructor
public class UpdateBlogWebhookUseCase {

  private final BlogWebhookRepository repository;

  @Transactional
  public BlogWebhookSummary execute(
      Long userId, Long id, String name, Set<BlogInteractionType> events, Boolean enabled) {
    BlogWebhookEntity hook =
        repository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new PostException(PostErrorCode.WEBHOOK_NOT_FOUND, id));
    hook.update(name, events, enabled);
    return BlogWebhookSummary.from(repository.save(hook));
  }
}
