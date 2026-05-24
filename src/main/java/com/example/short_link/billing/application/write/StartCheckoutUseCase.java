package com.example.short_link.billing.application.write;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingNotConfiguredException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Begin Pro checkout. Application owns: ensure billing is configured, look up the user, ask the
 * gateway to initiate, persist any newly-issued customer id back to the user. The Stripe SDK is
 * fully hidden behind {@link SubscriptionGateway} (Phase 1.1 ACL).
 */
@Service
@RequiredArgsConstructor
public class StartCheckoutUseCase {

  private final StripeProperties stripe;
  private final SubscriptionGateway subscriptions;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public String execute(StartCheckoutCommand cmd) {
    if (!stripe.isConfigured()) throw new BillingNotConfiguredException();
    UserEntity user = userRepository.findById(cmd.userId()).orElseThrow(UserNotFoundException::new);
    CheckoutInitiation init = subscriptions.initiateProCheckout(user);
    if (init.hasNewCustomer()) {
      user.linkStripeCustomer(init.newlyCreatedCustomerId());
    }
    meterRegistry.counter("billing.checkout.created").increment();
    return init.checkoutUrl();
  }
}
