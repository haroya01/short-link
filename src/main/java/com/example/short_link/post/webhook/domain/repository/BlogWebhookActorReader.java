package com.example.short_link.post.webhook.domain.repository;

import java.util.Optional;

/**
 * Read port for resolving the actor's display name when a webhook is about to fire. Lives in the
 * webhook module as a consumer port; the adapter reads the user module's {@code users} table by id.
 * Resolved lazily (only when a matching hook exists) so the hot interaction path never pays for it.
 */
public interface BlogWebhookActorReader {

  Optional<String> usernameOf(Long userId);
}
