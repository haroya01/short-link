package com.example.short_link.billing.domain;

import com.example.short_link.user.domain.UserEntity;

/**
 * Port for the Pro subscription lifecycle. Adapter handles all external SDK calls (Stripe today,
 * potentially Paddle/PayPay later) — application stays SDK-agnostic.
 *
 * <p>Contract: adapter owns the SDK flow (Customer create + Session create), application owns the
 * domain decision (whether to upgrade, whether to flip tier). Never put business policy here.
 */
public interface SubscriptionGateway {

  /**
   * Begin Pro checkout for the given user. If the user has no {@code stripeCustomerId}, the adapter
   * creates one and returns it as {@code newlyCreatedCustomerId} so the application can persist the
   * link.
   */
  CheckoutInitiation initiateProCheckout(UserEntity user);

  /** Issue a billing portal session for a user that already has a customer id. */
  PortalUrl issuePortalSession(String stripeCustomerId);

  /**
   * Parse and verify the raw webhook payload. Returns a domain event regardless of upstream type —
   * unknown event types map to {@link SubscriptionEvent.Ignored}.
   */
  SubscriptionEvent parseAndVerifyWebhook(String body, String signature);
}
