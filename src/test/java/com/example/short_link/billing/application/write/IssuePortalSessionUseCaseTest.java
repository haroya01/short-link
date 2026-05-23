package com.example.short_link.billing.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.application.BillingNotConfiguredException;
import com.example.short_link.billing.application.BillingNotEnrolledException;
import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.PortalUrl;
import com.example.short_link.billing.domain.SubscriptionGateway;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IssuePortalSessionUseCaseTest {

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
    assertThatThrownBy(() -> new IssuePortalSessionCommand(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectedWhenUnconfigured() {
    IssuePortalSessionUseCase useCase =
        new IssuePortalSessionUseCase(
            UNCONFIGURED,
            Mockito.mock(SubscriptionGateway.class),
            Mockito.mock(UserRepository.class),
            new SimpleMeterRegistry());
    assertThatThrownBy(() -> useCase.execute(new IssuePortalSessionCommand(1L)))
        .isInstanceOf(BillingNotConfiguredException.class);
  }

  @Test
  void throwsWhenUserMissing() {
    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(1L)).thenReturn(Optional.empty());
    IssuePortalSessionUseCase useCase =
        new IssuePortalSessionUseCase(
            CONFIGURED, Mockito.mock(SubscriptionGateway.class), users, new SimpleMeterRegistry());
    assertThatThrownBy(() -> useCase.execute(new IssuePortalSessionCommand(1L)))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void throwsWhenUserHasNoStripeCustomer() {
    UserEntity user = new UserEntity("u@x", "google", "g-1");
    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(1L)).thenReturn(Optional.of(user));

    IssuePortalSessionUseCase useCase =
        new IssuePortalSessionUseCase(
            CONFIGURED, Mockito.mock(SubscriptionGateway.class), users, new SimpleMeterRegistry());
    assertThatThrownBy(() -> useCase.execute(new IssuePortalSessionCommand(1L)))
        .isInstanceOf(BillingNotEnrolledException.class);
  }

  @Test
  void existingCustomerReturnsPortalUrl() {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-po"));
    Mockito.doReturn(3L).when(user).getId();
    user.linkStripeCustomer("cus_existing");

    UserRepository users = Mockito.mock(UserRepository.class);
    when(users.findById(3L)).thenReturn(Optional.of(user));

    SubscriptionGateway gateway = Mockito.mock(SubscriptionGateway.class);
    when(gateway.issuePortalSession("cus_existing"))
        .thenReturn(new PortalUrl("https://billing.stripe.com/p/x"));

    IssuePortalSessionUseCase useCase =
        new IssuePortalSessionUseCase(CONFIGURED, gateway, users, new SimpleMeterRegistry());
    String url = useCase.execute(new IssuePortalSessionCommand(3L));

    assertThat(url).isEqualTo("https://billing.stripe.com/p/x");
  }
}
