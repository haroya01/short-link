package com.example.short_link.billing.application;

import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pro subscription lifecycle. Three flows: Checkout, Portal, Webhook. Stripe (or any other
 * provider) lives entirely behind {@link SubscriptionGateway} — this class is SDK-agnostic.
 *
 * <p>The webhook is the source of truth for tier state. The Checkout success redirect just lands
 * the user back on the app — it does NOT mark them Pro by itself, because (a) the user could
 * navigate away before the redirect fires, and (b) we'd otherwise double-flip on the webhook.
 */
@Slf4j
@Service
public class BillingService {

  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;
  private final StripeProperties stripe;
  private final SubscriptionGateway subscriptions;

  public BillingService(
      UserRepository userRepository,
      MeterRegistry meterRegistry,
      StripeProperties stripe,
      SubscriptionGateway subscriptions) {
    this.userRepository = userRepository;
    this.meterRegistry = meterRegistry;
    this.stripe = stripe;
    this.subscriptions = subscriptions;
  }

  public boolean isConfigured() {
    return stripe.isConfigured();
  }

  @Transactional
  public String createCheckoutSession(Long userId) {
    requireConfigured();
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    CheckoutInitiation init = subscriptions.initiateProCheckout(user);
    if (init.hasNewCustomer()) {
      user.linkStripeCustomer(init.newlyCreatedCustomerId());
    }
    meterRegistry.counter("billing.checkout.created").increment();
    return init.checkoutUrl();
  }

  @Transactional
  public String createPortalSession(Long userId) {
    requireConfigured();
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getStripeCustomerId() == null) {
      throw new BillingNotEnrolledException();
    }
    PortalUrl url = subscriptions.issuePortalSession(user.getStripeCustomerId());
    meterRegistry.counter("billing.portal.created").increment();
    return url.url();
  }

  @Transactional
  public void handleWebhook(String payload, String signatureHeader) {
    SubscriptionEvent event;
    try {
      event = subscriptions.parseAndVerifyWebhook(payload, signatureHeader);
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

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new BillingNotConfiguredException();
    }
  }
}
