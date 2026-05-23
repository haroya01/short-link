package com.example.short_link.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BillingServiceCheckoutTest {

  private UserRepository userRepository;
  private SubscriptionGateway subscriptions;
  private BillingService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    subscriptions = Mockito.mock(SubscriptionGateway.class);
    service =
        new BillingService(
            userRepository,
            new SimpleMeterRegistry(),
            new StripeProperties(
                "sk_test",
                "whsec_test",
                "price_pro",
                "https://success",
                "https://cancel",
                "https://portal"),
            subscriptions);
  }

  @Test
  void createCheckoutSession_existingCustomer_doesNotRelink() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-co"));
    Mockito.doReturn(1L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(subscriptions.initiateProCheckout(user))
        .thenReturn(new CheckoutInitiation("https://checkout.stripe.com/c/x", null));

    String url = service.createCheckoutSession(1L);

    assertThat(url).isEqualTo("https://checkout.stripe.com/c/x");
    assertThat(user.getStripeCustomerId()).isEqualTo("cus_existing");
  }

  @Test
  void createCheckoutSession_newCustomer_linksReturnedId() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-cn"));
    Mockito.doReturn(2L).when(user).getId();
    when(userRepository.findById(2L)).thenReturn(Optional.of(user));
    when(subscriptions.initiateProCheckout(user))
        .thenReturn(new CheckoutInitiation("https://checkout.stripe.com/c/new", "cus_new"));

    String url = service.createCheckoutSession(2L);

    assertThat(url).isEqualTo("https://checkout.stripe.com/c/new");
    assertThat(user.getStripeCustomerId()).isEqualTo("cus_new");
  }

  @Test
  void createPortalSession_existingCustomer_returnsUrl() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-po"));
    Mockito.doReturn(3L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(3L)).thenReturn(Optional.of(user));
    when(subscriptions.issuePortalSession("cus_existing"))
        .thenReturn(new PortalUrl("https://billing.stripe.com/p/x"));

    String url = service.createPortalSession(3L);

    assertThat(url).isEqualTo("https://billing.stripe.com/p/x");
  }
}
