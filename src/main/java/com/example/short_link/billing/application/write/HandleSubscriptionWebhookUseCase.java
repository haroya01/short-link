package com.example.short_link.billing.application.write;

import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.InvalidWebhookSignatureException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Process a Stripe subscription webhook. The webhook is the source of truth for tier state — the
 * checkout success redirect never flips tier, only this path does.
 *
 * <p>Adapter ({@link SubscriptionGateway}) handles signature verification + event parsing.
 * Application only routes the parsed domain event to the corresponding user mutation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleSubscriptionWebhookUseCase {

  private final SubscriptionGateway subscriptions;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public void execute(SubscriptionWebhookCommand cmd) {
    SubscriptionEvent event;
    try {
      event = subscriptions.parseAndVerifyWebhook(cmd.payload(), cmd.signature());
    } catch (InvalidWebhookSignatureException e) {
      meterRegistry.counter("billing.webhook", "result", "invalid_signature").increment();
      throw e;
    }
    if (event instanceof SubscriptionEvent.CheckoutCompleted c) {
      onCheckoutCompleted(c);
    } else if (event instanceof SubscriptionEvent.SubscriptionUpdated u) {
      onSubscriptionUpdated(u);
    } else if (event instanceof SubscriptionEvent.SubscriptionDeleted d) {
      onSubscriptionDeleted(d);
    } else if (event instanceof SubscriptionEvent.Ignored i) {
      log.debug("ignoring stripe event {}", i.eventType());
    }
    meterRegistry.counter("billing.webhook", "type", event.eventType(), "result", "ok").increment();
  }

  private void onCheckoutCompleted(SubscriptionEvent.CheckoutCompleted event) {
    String clientRefId = event.clientReferenceId();
    String customerId = event.customerId();
    if (clientRefId == null || customerId == null) return;
    try {
      Long userId = Long.valueOf(clientRefId);
      userRepository
          .findById(userId)
          .ifPresent(
              u -> {
                if (u.getStripeCustomerId() == null) {
                  u.linkStripeCustomer(customerId);
                }
              });
    } catch (NumberFormatException ignored) {
      log.warn("checkout.session.completed had non-numeric client_reference_id: {}", clientRefId);
    }
  }

  private void onSubscriptionUpdated(SubscriptionEvent.SubscriptionUpdated event) {
    UserEntity user = userRepository.findByStripeCustomerId(event.customerId()).orElse(null);
    if (user == null) {
      log.warn("subscription event for unknown customer {}", event.customerId());
      return;
    }
    user.applySubscription(event.subscriptionId(), event.status(), event.currentPeriodEnd());
  }

  private void onSubscriptionDeleted(SubscriptionEvent.SubscriptionDeleted event) {
    UserEntity user = userRepository.findByStripeCustomerId(event.customerId()).orElse(null);
    if (user == null) {
      log.warn("subscription event for unknown customer {}", event.customerId());
      return;
    }
    user.clearSubscription();
  }
}
