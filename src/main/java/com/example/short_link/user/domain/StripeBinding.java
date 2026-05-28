package com.example.short_link.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stripe-side identifiers + current subscription snapshot grouped into one embeddable so the
 * authentication / profile concerns on {@link UserEntity} stop sitting next to billing columns. No
 * schema change — same column names live under {@link jakarta.persistence.Embedded} now.
 *
 * <p>Mutation methods mirror the legacy {@code UserEntity.linkStripeCustomer / applySubscription /
 * clearSubscription} contracts so callers see the same behaviour at the entity surface.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class StripeBinding {

  @Column(name = "stripe_customer_id", length = 64)
  private String stripeCustomerId;

  @Column(name = "stripe_subscription_id", length = 64)
  private String stripeSubscriptionId;

  @Column(name = "subscription_status", length = 32)
  private String subscriptionStatus;

  @Column(name = "subscription_current_period_end")
  private Instant subscriptionCurrentPeriodEnd;

  void linkCustomer(String customerId) {
    this.stripeCustomerId = customerId;
  }

  void applySubscription(String subscriptionId, String status, Instant currentPeriodEnd) {
    this.stripeSubscriptionId = subscriptionId;
    this.subscriptionStatus = status;
    this.subscriptionCurrentPeriodEnd = currentPeriodEnd;
  }

  void clearSubscription() {
    this.stripeSubscriptionId = null;
    this.subscriptionStatus = null;
    this.subscriptionCurrentPeriodEnd = null;
  }
}
