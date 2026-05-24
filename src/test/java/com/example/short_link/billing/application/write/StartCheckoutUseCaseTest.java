package com.example.short_link.billing.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.CheckoutInitiation;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.billing.exception.BillingNotConfiguredException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class StartCheckoutUseCaseTest {

  private static final StripeProperties CONFIGURED =
      new StripeProperties(
          "sk_test_x",
          "whsec_x",
          "price_pro",
          "https://app/success",
          "https://app/cancel",
          "https://app/portal");

  private static final StripeProperties UNCONFIGURED =
      new StripeProperties("", "", "", "https://app", "https://app", "https://app");

  @Test
  void commandRejectsNullUserId() {
    assertThatThrownBy(() -> new StartCheckoutCommand(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectedWhenUnconfigured() {
    StartCheckoutUseCase useCase =
        new StartCheckoutUseCase(
            UNCONFIGURED,
            Mockito.mock(SubscriptionGateway.class),
            Mockito.mock(UserRepository.class),
            new SimpleMeterRegistry());
    assertThatThrownBy(() -> useCase.execute(new StartCheckoutCommand(1L)))
        .isInstanceOf(BillingNotConfiguredException.class);
  }

  @Test
  void throwsWhenUserMissing() {
    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(1L)).thenReturn(Optional.empty());
    StartCheckoutUseCase useCase =
        new StartCheckoutUseCase(
            CONFIGURED, Mockito.mock(SubscriptionGateway.class), users, new SimpleMeterRegistry());
    assertThatThrownBy(() -> useCase.execute(new StartCheckoutCommand(1L)))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void existingCustomerSkipsLink() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-co"));
    Mockito.doReturn(1L).when(user).getId();
    user.linkStripeCustomer("cus_existing");

    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(1L)).thenReturn(Optional.of(user));

    SubscriptionGateway gateway = Mockito.mock(SubscriptionGateway.class);
    when(gateway.initiateProCheckout(user))
        .thenReturn(new CheckoutInitiation("https://checkout.stripe.com/c/x", null));

    StartCheckoutUseCase useCase =
        new StartCheckoutUseCase(CONFIGURED, gateway, users, new SimpleMeterRegistry());
    String url = useCase.execute(new StartCheckoutCommand(1L));

    assertThat(url).isEqualTo("https://checkout.stripe.com/c/x");
    assertThat(user.getStripeCustomerId()).isEqualTo("cus_existing");
  }

  @Test
  void newCustomerLinksReturnedId() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-cn"));
    Mockito.doReturn(2L).when(user).getId();

    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(2L)).thenReturn(Optional.of(user));

    SubscriptionGateway gateway = Mockito.mock(SubscriptionGateway.class);
    when(gateway.initiateProCheckout(user))
        .thenReturn(new CheckoutInitiation("https://checkout.stripe.com/c/new", "cus_new"));

    StartCheckoutUseCase useCase =
        new StartCheckoutUseCase(CONFIGURED, gateway, users, new SimpleMeterRegistry());
    String url = useCase.execute(new StartCheckoutCommand(2L));

    assertThat(url).isEqualTo("https://checkout.stripe.com/c/new");
    assertThat(user.getStripeCustomerId()).isEqualTo("cus_new");
  }
}
