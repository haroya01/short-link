package com.example.short_link.post.webhook.scheduler;

import com.example.short_link.common.crypto.SecretCipher;
import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.webhook.WebhookSender;
import com.example.short_link.post.webhook.application.helper.BlogWebhookPayloadAdapter;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookActorReader;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Notifies an author's blog webhooks when a reader likes/comments/follows/subscribes. Fires only
 * after the interaction commits (so a rolled-back like never sends a phantom notification) and runs
 * on the shared {@code webhookExecutor} so a slow receiver can't stall the request path. The
 * actor's name is resolved here, lazily, only once a matching enabled hook is confirmed to exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlogWebhookDispatcher {

  private final BlogWebhookRepository repository;
  private final BlogWebhookActorReader actorReader;
  private final JsonMapper jsonMapper;
  private final HttpFetcher httpFetcher;
  private final MeterRegistry meterRegistry;
  private final SecretCipher cipher;

  @Async("webhookExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onBlogInteraction(BlogInteractionEvent event) {
    if (event.isSelfAction() || event.recipientUserId() == null) {
      return;
    }
    List<BlogWebhookEntity> hooks =
        repository.findAllByUserIdAndEnabledTrue(event.recipientUserId()).stream()
            .filter(h -> h.firesOn(event.type()))
            .toList();
    if (hooks.isEmpty()) {
      return;
    }
    String actor = actorReader.usernameOf(event.actorUserId()).orElse("someone");
    String eventType = BlogWebhookPayloadAdapter.eventType(event.type());
    for (BlogWebhookEntity hook : hooks) {
      Map<String, Object> body = BlogWebhookPayloadAdapter.build(hook.getFormat(), event, actor);
      deliver(hook, jsonMapper.writeValueAsString(body), eventType);
    }
  }

  private void deliver(BlogWebhookEntity hook, String body, String eventType) {
    WebhookSender.Result result =
        WebhookSender.send(
            httpFetcher,
            meterRegistry,
            hook.getUrl(),
            cipher.decrypt(hook.getSecret()),
            hook.signed(),
            body,
            eventType);
    if (result.ok()) {
      hook.recordSuccess(result.statusCode());
    } else {
      hook.recordFailure(result.statusCode(), result.error());
      log.warn(
          "blog webhook delivery failed: hookId={} outcome={} reason={}",
          hook.getId(),
          result.outcome(),
          result.error());
    }
  }
}
