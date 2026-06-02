package com.example.short_link.billing.application.write;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingErrorCode;
import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Issue a Stripe billing portal session for a user that already has a customer id. Throws {@link
 * BillingException} (409) when the user never went through Pro checkout — application decides this,
 * not the gateway.
 */
@Service
@RequiredArgsConstructor
public class IssuePortalSessionUseCase {

  private final StripeProperties stripe;
  private final SubscriptionGateway subscriptions;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;

  // No @Transactional: this only reads the user and calls Stripe (1-3s external IO). Holding a DB
  // transaction open across that call would pin a pooled connection for the whole round-trip.
  public String execute(IssuePortalSessionCommand cmd) {
    if (!stripe.isConfigured()) throw new BillingException(BillingErrorCode.BILLING_NOT_CONFIGURED);
    UserEntity user =
        userRepository
            .findById(cmd.userId())
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.getStripeCustomerId() == null) {
      throw new BillingException(BillingErrorCode.BILLING_NOT_ENROLLED);
    }
    PortalUrl url = subscriptions.issuePortalSession(user.getStripeCustomerId());
    meterRegistry.counter("billing.portal.created").increment();
    return url.url();
  }
}
