package com.example.short_link.billing.application.write;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingNotConfiguredException;
import com.example.short_link.billing.exception.BillingNotEnrolledException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issue a Stripe billing portal session for a user that already has a customer id. Throws {@link
 * BillingNotEnrolledException} (409) when the user never went through Pro checkout — application
 * decides this, not the gateway.
 */
@Service
@RequiredArgsConstructor
public class IssuePortalSessionUseCase {

  private final StripeProperties stripe;
  private final SubscriptionGateway subscriptions;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public String execute(IssuePortalSessionCommand cmd) {
    if (!stripe.isConfigured()) throw new BillingNotConfiguredException();
    UserEntity user = userRepository.findById(cmd.userId()).orElseThrow(UserNotFoundException::new);
    if (user.getStripeCustomerId() == null) {
      throw new BillingNotEnrolledException();
    }
    PortalUrl url = subscriptions.issuePortalSession(user.getStripeCustomerId());
    meterRegistry.counter("billing.portal.created").increment();
    return url.url();
  }
}
