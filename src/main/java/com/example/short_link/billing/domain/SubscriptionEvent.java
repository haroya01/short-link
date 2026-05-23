package com.example.short_link.billing.domain;

import java.time.Instant;

public sealed interface SubscriptionEvent
    permits SubscriptionEvent.CheckoutCompleted,
        SubscriptionEvent.SubscriptionUpdated,
        SubscriptionEvent.SubscriptionDeleted,
        SubscriptionEvent.Ignored {

  String eventType();

  record CheckoutCompleted(String eventType, String clientReferenceId, String customerId)
      implements SubscriptionEvent {}

  record SubscriptionUpdated(
      String eventType,
      String customerId,
      String subscriptionId,
      String status,
      Instant currentPeriodEnd)
      implements SubscriptionEvent {}

  record SubscriptionDeleted(String eventType, String customerId) implements SubscriptionEvent {}

  record Ignored(String eventType) implements SubscriptionEvent {}
}
