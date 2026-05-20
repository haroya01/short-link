package com.example.short_link.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.stripe.model.Customer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class BillingServiceCheckoutTest {

  private UserRepository userRepository;
  private BillingService service;

  @BeforeEach
  void setUp() {
    userRepository = Mockito.mock(UserRepository.class);
    service =
        new BillingService(
            userRepository,
            new SimpleMeterRegistry(),
            "sk_test",
            "whsec_test",
            "price_pro",
            "https://success",
            "https://cancel",
            "https://portal");
  }

  @Test
  void createCheckoutSession_existingCustomer_doesNotCreateNew() throws Exception {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-co"));
    Mockito.doReturn(1L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    com.stripe.model.checkout.Session stripeSession =
        Mockito.mock(com.stripe.model.checkout.Session.class);
    when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/c/x");

    try (MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
            mockStatic(com.stripe.model.checkout.Session.class);
        MockedStatic<Customer> customerStatic = mockStatic(Customer.class)) {
      sessionStatic
          .when(
              () ->
                  com.stripe.model.checkout.Session.create(
                      any(com.stripe.param.checkout.SessionCreateParams.class)))
          .thenReturn(stripeSession);

      String url = service.createCheckoutSession(1L);

      assertThat(url).isEqualTo("https://checkout.stripe.com/c/x");
      // 기존 customer 있으면 Customer.create 호출되지 않아야
      customerStatic.verifyNoInteractions();
    }
  }

  @Test
  void createCheckoutSession_newCustomer_createsViaStripe() throws Exception {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-cn"));
    Mockito.doReturn(2L).when(user).getId();
    // no linked customer
    when(userRepository.findById(2L)).thenReturn(Optional.of(user));

    Customer newCustomer = Mockito.mock(Customer.class);
    when(newCustomer.getId()).thenReturn("cus_new");

    com.stripe.model.checkout.Session stripeSession =
        Mockito.mock(com.stripe.model.checkout.Session.class);
    when(stripeSession.getUrl()).thenReturn("https://checkout.stripe.com/c/new");

    try (MockedStatic<Customer> customerStatic = mockStatic(Customer.class);
        MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
            mockStatic(com.stripe.model.checkout.Session.class)) {
      customerStatic
          .when(() -> Customer.create(any(com.stripe.param.CustomerCreateParams.class)))
          .thenReturn(newCustomer);
      sessionStatic
          .when(
              () ->
                  com.stripe.model.checkout.Session.create(
                      any(com.stripe.param.checkout.SessionCreateParams.class)))
          .thenReturn(stripeSession);

      String url = service.createCheckoutSession(2L);

      assertThat(url).isEqualTo("https://checkout.stripe.com/c/new");
      assertThat(user.getStripeCustomerId()).isEqualTo("cus_new");
    }
  }

  @Test
  void createPortalSession_existingCustomer_returnsUrl() throws Exception {
    UserEntity user = Mockito.spy(new UserEntity("u@x.com", "google", "g-po"));
    Mockito.doReturn(3L).when(user).getId();
    user.linkStripeCustomer("cus_existing");
    when(userRepository.findById(3L)).thenReturn(Optional.of(user));

    com.stripe.model.billingportal.Session portalSession =
        Mockito.mock(com.stripe.model.billingportal.Session.class);
    when(portalSession.getUrl()).thenReturn("https://billing.stripe.com/p/x");

    try (MockedStatic<com.stripe.model.billingportal.Session> portalStatic =
        mockStatic(com.stripe.model.billingportal.Session.class)) {
      portalStatic
          .when(
              () ->
                  com.stripe.model.billingportal.Session.create(
                      any(com.stripe.param.billingportal.SessionCreateParams.class)))
          .thenReturn(portalSession);

      String url = service.createPortalSession(3L);

      assertThat(url).isEqualTo("https://billing.stripe.com/p/x");
    }
  }
}
