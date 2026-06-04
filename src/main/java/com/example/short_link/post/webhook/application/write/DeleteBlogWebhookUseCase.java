package com.example.short_link.post.webhook.application.write;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteBlogWebhookUseCase {

  private final BlogWebhookRepository repository;

  @Transactional
  public void execute(Long userId, Long id) {
    BlogWebhookEntity hook =
        repository
            .findByIdAndUserId(id, userId)
            .orElseThrow(() -> new PostException(PostErrorCode.WEBHOOK_NOT_FOUND, id));
    repository.delete(hook);
  }
}
