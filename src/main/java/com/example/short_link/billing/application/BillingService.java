package com.example.short_link.billing.application;

import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stripe-backed Pro subscription lifecycle.
 *
 * <p>Three flows:
 *
 * <ol>
 *   <li>Checkout — create a Customer (idempotent on stripeCustomerId), then a hosted Checkout
 *       Session for the Pro price. User redirects to Stripe.
 *   <li>Portal — for existing Pro users; surface Stripe's billing portal so they can update card,
 *       cancel, see invoices. We never touch payment data ourselves.
 *   <li>Webhook — receive subscription.* and checkout.session.completed, mark the matching user as
 *       Pro / Free based on subscription status. This is the only path that flips tier.
 * </ol>
 *
 * <p>The webhook is the source of truth. The Checkout success redirect just lands the user back on
 * the app — it does NOT mark them Pro by itself, because (a) the user could navigate away before
 * the redirect fires, and (b) we'd otherwise double-flip on the webhook.
 */
@Slf4j
@Service
public class BillingService {

  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;
  private final StripeProperties stripe;

  public BillingService(
      UserRepository userRepository, MeterRegistry meterRegistry, StripeProperties stripe) {
    this.userRepository = userRepository;
    this.meterRegistry = meterRegistry;
    this.stripe = stripe;
  }

  @PostConstruct
  void init() {
    if (stripe.apiKey() != null && !stripe.apiKey().isBlank()) {
      Stripe.apiKey = stripe.apiKey();
    } else {
      log.warn("Stripe API key not configured — billing endpoints will reject requests");
    }
  }

  public boolean isConfigured() {
    return stripe.isConfigured();
  }

  @Transactional
  public String createCheckoutSession(Long userId) throws StripeException {
    requireConfigured();
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    String customerId = ensureCustomer(user);

    com.stripe.param.checkout.SessionCreateParams params =
        com.stripe.param.checkout.SessionCreateParams.builder()
            .setMode(Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .setSuccessUrl(stripe.successUrl())
            .setCancelUrl(stripe.cancelUrl())
            .addLineItem(LineItem.builder().setPrice(stripe.pricePro()).setQuantity(1L).build())
            .setClientReferenceId(String.valueOf(userId))
            .build();
    com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(params);
    meterRegistry.counter("billing.checkout.created").increment();
    return session.getUrl();
  }

  @Transactional
  public String createPortalSession(Long userId) throws StripeException {
    requireConfigured();
    UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getStripeCustomerId() == null) {
      throw new BillingNotEnrolledException();
    }
    SessionCreateParams params =
        SessionCreateParams.builder()
            .setCustomer(user.getStripeCustomerId())
            .setReturnUrl(stripe.portalReturnUrl())
            .build();
    com.stripe.model.billingportal.Session session =
        com.stripe.model.billingportal.Session.create(params);
    meterRegistry.counter("billing.portal.created").increment();
    return session.getUrl();
  }

  @Transactional
  public void handleWebhook(String payload, String signatureHeader) {
    if (stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
      throw new IllegalStateException("Stripe webhook secret not configured");
    }
    Event event;
    try {
      event = Webhook.constructEvent(payload, signatureHeader, stripe.webhookSecret());
    } catch (SignatureVerificationException e) {
      meterRegistry.counter("billing.webhook", "result", "invalid_signature").increment();
      throw new InvalidWebhookSignatureException();
    }

    switch (event.getType()) {
      case "checkout.session.completed" -> onCheckoutCompleted(event);
      case "customer.subscription.created",
              "customer.subscription.updated",
              "customer.subscription.deleted",
              "customer.subscription.paused",
              "customer.subscription.resumed" ->
          onSubscriptionEvent(event);
      default -> log.debug("ignoring stripe event {}", event.getType());
    }
    meterRegistry.counter("billing.webhook", "type", event.getType(), "result", "ok").increment();
  }

  private void onCheckoutCompleted(Event event) {
    com.stripe.model.checkout.Session session =
        (com.stripe.model.checkout.Session)
            event.getDataObjectDeserializer().getObject().orElse(null);
    if (session == null) return;
    String clientRefId = session.getClientReferenceId();
    String customerId = session.getCustomer();
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

  private void onSubscriptionEvent(Event event) {
    Subscription subscription =
        (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
    if (subscription == null) return;
    String customerId = subscription.getCustomer();
    if (customerId == null) return;
    UserEntity user = userRepository.findByStripeCustomerId(customerId).orElse(null);
    if (user == null) {
      log.warn("subscription event for unknown customer {}", customerId);
      return;
    }
    Instant periodEnd =
        subscription.getCurrentPeriodEnd() == null
            ? null
            : Instant.ofEpochSecond(subscription.getCurrentPeriodEnd());
    if ("customer.subscription.deleted".equals(event.getType())) {
      user.clearSubscription();
    } else {
      user.applySubscription(subscription.getId(), subscription.getStatus(), periodEnd);
    }
  }

  private String ensureCustomer(UserEntity user) throws StripeException {
    if (user.getStripeCustomerId() != null) return user.getStripeCustomerId();
    Customer customer =
        Customer.create(
            CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .putMetadata("userId", String.valueOf(user.getId()))
                .build());
    user.linkStripeCustomer(customer.getId());
    return customer.getId();
  }

  private void requireConfigured() {
    if (!isConfigured()) {
      throw new BillingNotConfiguredException();
    }
  }
}
