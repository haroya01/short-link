package com.example.short_link.billing.infrastructure.stripe;

import com.example.short_link.billing.application.BillingGatewayException;
import com.example.short_link.billing.application.InvalidWebhookSignatureException;
import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.domain.UserEntity;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeSubscriptionGateway implements SubscriptionGateway {

  private final StripeProperties stripe;

  @PostConstruct
  void init() {
    if (stripe.apiKey() != null && !stripe.apiKey().isBlank()) {
      Stripe.apiKey = stripe.apiKey();
    } else {
      log.warn("Stripe API key not configured — billing endpoints will reject requests");
    }
  }

  @Override
  public CheckoutInitiation initiateProCheckout(UserEntity user) {
    try {
      String customerId = user.getStripeCustomerId();
      String newlyCreated = null;
      if (customerId == null) {
        Customer customer =
            Customer.create(
                CustomerCreateParams.builder()
                    .setEmail(user.getEmail())
                    .putMetadata("userId", String.valueOf(user.getId()))
                    .build());
        customerId = customer.getId();
        newlyCreated = customerId;
      }
      com.stripe.param.checkout.SessionCreateParams params =
          com.stripe.param.checkout.SessionCreateParams.builder()
              .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.SUBSCRIPTION)
              .setCustomer(customerId)
              .setSuccessUrl(stripe.successUrl())
              .setCancelUrl(stripe.cancelUrl())
              .addLineItem(
                  com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                      .setPrice(stripe.pricePro())
                      .setQuantity(1L)
                      .build())
              .setClientReferenceId(String.valueOf(user.getId()))
              .build();
      com.stripe.model.checkout.Session session = com.stripe.model.checkout.Session.create(params);
      return new CheckoutInitiation(session.getUrl(), newlyCreated);
    } catch (StripeException e) {
      throw new BillingGatewayException("stripe checkout failed", e);
    }
  }

  @Override
  public PortalUrl issuePortalSession(String stripeCustomerId) {
    try {
      com.stripe.param.billingportal.SessionCreateParams params =
          com.stripe.param.billingportal.SessionCreateParams.builder()
              .setCustomer(stripeCustomerId)
              .setReturnUrl(stripe.portalReturnUrl())
              .build();
      com.stripe.model.billingportal.Session session =
          com.stripe.model.billingportal.Session.create(params);
      return new PortalUrl(session.getUrl());
    } catch (StripeException e) {
      throw new BillingGatewayException("stripe portal session failed", e);
    }
  }

  @Override
  public SubscriptionEvent parseAndVerifyWebhook(String body, String signature) {
    if (stripe.webhookSecret() == null || stripe.webhookSecret().isBlank()) {
      throw new IllegalStateException("Stripe webhook secret not configured");
    }
    Event event;
    try {
      event = Webhook.constructEvent(body, signature, stripe.webhookSecret());
    } catch (SignatureVerificationException e) {
      throw new InvalidWebhookSignatureException();
    }
    return switch (event.getType()) {
      case "checkout.session.completed" -> parseCheckoutCompleted(event);
      case "customer.subscription.deleted" -> parseSubscriptionDeleted(event);
      case "customer.subscription.created",
              "customer.subscription.updated",
              "customer.subscription.paused",
              "customer.subscription.resumed" ->
          parseSubscriptionUpdated(event);
      default -> new SubscriptionEvent.Ignored(event.getType());
    };
  }

  private SubscriptionEvent parseCheckoutCompleted(Event event) {
    com.stripe.model.checkout.Session session =
        (com.stripe.model.checkout.Session)
            event.getDataObjectDeserializer().getObject().orElse(null);
    if (session == null) return new SubscriptionEvent.Ignored(event.getType());
    return new SubscriptionEvent.CheckoutCompleted(
        event.getType(), session.getClientReferenceId(), session.getCustomer());
  }

  private SubscriptionEvent parseSubscriptionUpdated(Event event) {
    Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
    if (sub == null) return new SubscriptionEvent.Ignored(event.getType());
    Instant end =
        sub.getCurrentPeriodEnd() == null ? null : Instant.ofEpochSecond(sub.getCurrentPeriodEnd());
    return new SubscriptionEvent.SubscriptionUpdated(
        event.getType(), sub.getCustomer(), sub.getId(), sub.getStatus(), end);
  }

  private SubscriptionEvent parseSubscriptionDeleted(Event event) {
    Subscription sub = (Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
    if (sub == null) return new SubscriptionEvent.Ignored(event.getType());
    return new SubscriptionEvent.SubscriptionDeleted(event.getType(), sub.getCustomer());
  }
}
