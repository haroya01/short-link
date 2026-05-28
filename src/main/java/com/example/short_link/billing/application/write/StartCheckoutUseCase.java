package com.example.short_link.billing.application.write;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingErrorCode;
import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Begin Pro checkout. Application owns: ensure billing is configured, look up the user, ask the
 * gateway to initiate, persist any newly-issued customer id back to the user. The Stripe SDK is
 * fully hidden behind {@link SubscriptionGateway} (Phase 1.1 ACL).
 *
 * <p>The Stripe HTTP call runs outside any open transaction so a slow Stripe round-trip (1-3s
 * typical) does not hold a DB connection. The follow-up customer-id write is a short {@code
 * TransactionTemplate} block.
 */
@Service
public class StartCheckoutUseCase {

  private final StripeProperties stripe;
  private final SubscriptionGateway subscriptions;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate tx;

  public StartCheckoutUseCase(
      StripeProperties stripe,
      SubscriptionGateway subscriptions,
      UserRepository userRepository,
      MeterRegistry meterRegistry,
      PlatformTransactionManager transactionManager) {
    this.stripe = stripe;
    this.subscriptions = subscriptions;
    this.userRepository = userRepository;
    this.meterRegistry = meterRegistry;
    this.tx = new TransactionTemplate(transactionManager);
  }

  public String execute(StartCheckoutCommand cmd) {
    if (!stripe.isConfigured()) throw new BillingException(BillingErrorCode.BILLING_NOT_CONFIGURED);
    UserEntity user =
        userRepository
            .findById(cmd.userId())
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    CheckoutInitiation init = subscriptions.initiateProCheckout(user);
    if (init.hasNewCustomer()) {
      tx.executeWithoutResult(
          status -> {
            UserEntity fresh =
                userRepository
                    .findById(cmd.userId())
                    .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
            fresh.linkStripeCustomer(init.newlyCreatedCustomerId());
          });
    }
    meterRegistry.counter("billing.checkout.created").increment();
    return init.checkoutUrl();
  }
}
