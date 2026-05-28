package com.example.short_link.billing.infrastructure.stripe;

import static com.stripe.model.billingportal.Session.create;
import static com.stripe.param.billingportal.SessionCreateParams.builder;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingErrorCode;
import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.user.domain.UserEntity;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
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
      SessionCreateParams params =
          SessionCreateParams.builder()
              .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
              .setCustomer(customerId)
              .setSuccessUrl(stripe.successUrl())
              .setCancelUrl(stripe.cancelUrl())
              .addLineItem(
                  SessionCreateParams.LineItem.builder()
                      .setPrice(stripe.pricePro())
                      .setQuantity(1L)
                      .build())
              .setClientReferenceId(String.valueOf(user.getId()))
              .build();
      Session session = Session.create(params);
      return new CheckoutInitiation(session.getUrl(), newlyCreated);
    } catch (StripeException e) {
      throw new BillingException(BillingErrorCode.BILLING_GATEWAY_ERROR, e);
    }
  }

  @Override
  public PortalUrl issuePortalSession(String stripeCustomerId) {
    try {
      var params =
          builder().setCustomer(stripeCustomerId).setReturnUrl(stripe.portalReturnUrl()).build();
      var session = create(params);
      return new PortalUrl(session.getUrl());
    } catch (StripeException e) {
      throw new BillingException(BillingErrorCode.BILLING_GATEWAY_ERROR, e);
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
      throw new BillingException(BillingErrorCode.INVALID_WEBHOOK_SIGNATURE);
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
    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
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
