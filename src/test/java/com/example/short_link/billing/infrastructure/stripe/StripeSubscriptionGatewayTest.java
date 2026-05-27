package com.example.short_link.billing.infrastructure.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.billing.application.StripeProperties;
import com.example.short_link.billing.domain.SubscriptionEvent;
import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.user.domain.UserEntity;
import com.stripe.Stripe;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Subscription;
import com.stripe.model.billingportal.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.billingportal.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class StripeSubscriptionGatewayTest {

  private static StripeProperties props(String apiKey, String webhookSecret) {
    return new StripeProperties(
        apiKey,
        webhookSecret,
        "price_pro",
        "https://app/success",
        "https://app/cancel",
        "https://app/portal");
  }

  private static UserEntity user(String customerId) {
    UserEntity u = new UserEntity("a@x.com", "google", "g-1");
    if (customerId != null) u.linkStripeCustomer(customerId);
    return u;
  }

  @Test
  void initSetsStripeApiKeyWhenProvided() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_live_X", null));
    gateway.init();
    assertThat(Stripe.apiKey).isEqualTo("sk_live_X");
  }

  @Test
  void initSkipsWhenApiKeyBlank() {
    Stripe.apiKey = "previous";
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("", null));
    gateway.init();
    assertThat(Stripe.apiKey).isEqualTo("previous");
  }

  @Test
  void initiateProCheckoutCreatesCustomerWhenMissing() throws Exception {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", null));
    Customer customer = mock(Customer.class);
    when(customer.getId()).thenReturn("cus_new");
    com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
    when(session.getUrl()).thenReturn("https://checkout/abc");

    try (MockedStatic<Customer> customerStatic = mockStatic(Customer.class);
        MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
            mockStatic(com.stripe.model.checkout.Session.class)) {
      customerStatic
          .when(() -> Customer.create(any(CustomerCreateParams.class)))
          .thenReturn(customer);
      sessionStatic
          .when(
              () ->
                  com.stripe.model.checkout.Session.create(
                      any(com.stripe.param.checkout.SessionCreateParams.class)))
          .thenReturn(session);

      var initiation = gateway.initiateProCheckout(user(null));

      assertThat(initiation.checkoutUrl()).isEqualTo("https://checkout/abc");
      assertThat(initiation.newlyCreatedCustomerId()).isEqualTo("cus_new");
    }
  }

  @Test
  void initiateProCheckoutReusesExistingCustomer() throws Exception {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", null));
    com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
    when(session.getUrl()).thenReturn("https://checkout/xyz");

    try (MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
        mockStatic(com.stripe.model.checkout.Session.class)) {
      sessionStatic
          .when(
              () ->
                  com.stripe.model.checkout.Session.create(
                      any(com.stripe.param.checkout.SessionCreateParams.class)))
          .thenReturn(session);

      var initiation = gateway.initiateProCheckout(user("cus_existing"));

      assertThat(initiation.checkoutUrl()).isEqualTo("https://checkout/xyz");
      assertThat(initiation.newlyCreatedCustomerId()).isNull();
    }
  }

  @Test
  void initiateProCheckoutWrapsStripeExceptionAsBilling() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", null));

    try (MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
        mockStatic(com.stripe.model.checkout.Session.class)) {
      sessionStatic
          .when(
              () ->
                  com.stripe.model.checkout.Session.create(
                      any(com.stripe.param.checkout.SessionCreateParams.class)))
          .thenThrow(new ApiException("boom", null, null, 500, null));

      assertThatThrownBy(() -> gateway.initiateProCheckout(user("cus_x")))
          .isInstanceOf(BillingException.class);
    }
  }

  @Test
  void issuePortalSessionReturnsUrl() throws Exception {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", null));
    Session portal = mock(Session.class);
    when(portal.getUrl()).thenReturn("https://portal/abc");

    try (MockedStatic<Session> portalStatic = mockStatic(Session.class)) {
      portalStatic.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(portal);

      var url = gateway.issuePortalSession("cus_x");

      assertThat(url.url()).isEqualTo("https://portal/abc");
    }
  }

  @Test
  void issuePortalSessionWrapsStripeExceptionAsBilling() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", null));

    try (MockedStatic<Session> portalStatic = mockStatic(Session.class)) {
      portalStatic
          .when(() -> Session.create(any(SessionCreateParams.class)))
          .thenThrow(new ApiException("boom", null, null, 500, null));

      assertThatThrownBy(() -> gateway.issuePortalSession("cus_x"))
          .isInstanceOf(BillingException.class);
    }
  }

  @Test
  void webhookRejectsWhenSecretMissing() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", ""));

    assertThatThrownBy(() -> gateway.parseAndVerifyWebhook("body", "sig"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void webhookSignatureFailureMapsToBilling() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic
          .when(() -> Webhook.constructEvent("b", "s", "whsec_x"))
          .thenThrow(new SignatureVerificationException("bad", "s"));

      assertThatThrownBy(() -> gateway.parseAndVerifyWebhook("b", "s"))
          .isInstanceOf(BillingException.class);
    }
  }

  @Test
  void webhookReturnsIgnoredForUnknownType() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("invoice.created");

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.Ignored.class);
      assertThat(parsed.eventType()).isEqualTo("invoice.created");
    }
  }

  @Test
  void webhookParsesCheckoutCompleted() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
    when(session.getClientReferenceId()).thenReturn("42");
    when(session.getCustomer()).thenReturn("cus_42");
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.of(session));

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.CheckoutCompleted.class);
      SubscriptionEvent.CheckoutCompleted cc = (SubscriptionEvent.CheckoutCompleted) parsed;
      assertThat(cc.clientReferenceId()).isEqualTo("42");
      assertThat(cc.customerId()).isEqualTo("cus_42");
    }
  }

  @Test
  void webhookCheckoutCompletedWithNoSessionIsIgnored() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.empty());

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.Ignored.class);
    }
  }

  @Test
  void webhookParsesSubscriptionUpdated() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.updated");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    Subscription sub = mock(Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_99");
    when(sub.getId()).thenReturn("sub_99");
    when(sub.getStatus()).thenReturn("active");
    when(sub.getCurrentPeriodEnd()).thenReturn(1700000000L);
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.of(sub));

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.SubscriptionUpdated.class);
      SubscriptionEvent.SubscriptionUpdated u = (SubscriptionEvent.SubscriptionUpdated) parsed;
      assertThat(u.customerId()).isEqualTo("cus_99");
      assertThat(u.status()).isEqualTo("active");
      assertThat(u.currentPeriodEnd().getEpochSecond()).isEqualTo(1700000000L);
    }
  }

  @Test
  void webhookParsesSubscriptionUpdatedWithNullPeriodEnd() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.created");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    Subscription sub = mock(Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_n");
    when(sub.getId()).thenReturn("sub_n");
    when(sub.getStatus()).thenReturn("trialing");
    when(sub.getCurrentPeriodEnd()).thenReturn(null);
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.of(sub));

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      SubscriptionEvent.SubscriptionUpdated u = (SubscriptionEvent.SubscriptionUpdated) parsed;
      assertThat(u.currentPeriodEnd()).isNull();
    }
  }

  @Test
  void webhookSubscriptionUpdatedWithNoSubscriptionIsIgnored() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.paused");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.empty());

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.Ignored.class);
    }
  }

  @Test
  void webhookParsesSubscriptionDeleted() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.deleted");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    Subscription sub = mock(Subscription.class);
    when(sub.getCustomer()).thenReturn("cus_del");
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.of(sub));

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.SubscriptionDeleted.class);
      assertThat(((SubscriptionEvent.SubscriptionDeleted) parsed).customerId())
          .isEqualTo("cus_del");
    }
  }

  @Test
  void webhookSubscriptionDeletedWithNoSubscriptionIsIgnored() {
    StripeSubscriptionGateway gateway = new StripeSubscriptionGateway(props("sk_test", "whsec_x"));
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("customer.subscription.deleted");
    EventDataObjectDeserializer ded = mock(EventDataObjectDeserializer.class);
    when(event.getDataObjectDeserializer()).thenReturn(ded);
    when(ded.getObject()).thenReturn(Optional.empty());

    try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
      webhookStatic.when(() -> Webhook.constructEvent("b", "s", "whsec_x")).thenReturn(event);

      SubscriptionEvent parsed = gateway.parseAndVerifyWebhook("b", "s");

      assertThat(parsed).isInstanceOf(SubscriptionEvent.Ignored.class);
    }
  }

  // Reference for unused-import suppression — kept to clarify the file's intent.
  @SuppressWarnings("unused")
  private static final Mode MODE_REF = Mode.SUBSCRIPTION;
}
