package com.example.short_link.post.webhook.domain.repository;

import java.util.Optional;

/** Resolve the actor only after a matching hook is found to avoid work on the interaction path. */
public interface BlogWebhookActorReader {

  Optional<String> usernameOf(Long userId);
}
