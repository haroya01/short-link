package com.example.short_link.post.webhook.domain.repository;

import java.util.Optional;

public interface BlogWebhookActorReader {

  Optional<String> usernameOf(Long userId);
}
