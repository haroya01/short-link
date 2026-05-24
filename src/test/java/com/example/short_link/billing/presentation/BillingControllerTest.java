package com.example.short_link.billing.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.billing.application.write.HandleSubscriptionWebhookUseCase;
import com.example.short_link.billing.application.write.IssuePortalSessionCommand;
import com.example.short_link.billing.application.write.IssuePortalSessionUseCase;
import com.example.short_link.billing.application.write.StartCheckoutCommand;
import com.example.short_link.billing.application.write.StartCheckoutUseCase;
import com.example.short_link.billing.application.write.SubscriptionWebhookCommand;
import com.example.short_link.billing.exception.BillingErrorCode;
import com.example.short_link.billing.exception.BillingException;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private StartCheckoutUseCase startCheckout;
  @MockitoBean private IssuePortalSessionUseCase issuePortalSession;
  @MockitoBean private HandleSubscriptionWebhookUseCase handleWebhook;

  @Test
  void anonymousCheckoutIs401() throws Exception {
    mvc.perform(post("/api/v1/billing/checkout")).andExpect(status().isUnauthorized());
  }

  @Test
  void checkoutReturnsUrl() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-co"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(startCheckout.execute(new StartCheckoutCommand(user.getId())))
        .thenReturn("https://checkout.stripe.com/c/x");

    mvc.perform(post("/api/v1/billing/checkout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/x"));
  }

  @Test
  void checkoutWhenNotConfiguredReturns503() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-503"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(startCheckout.execute(any(StartCheckoutCommand.class)))
        .thenThrow(new BillingException(BillingErrorCode.BILLING_NOT_CONFIGURED));

    mvc.perform(post("/api/v1/billing/checkout").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("BILLING_NOT_CONFIGURED"));
  }

  @Test
  void portalReturnsUrl() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-po"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(issuePortalSession.execute(new IssuePortalSessionCommand(user.getId())))
        .thenReturn("https://billing.stripe.com/p/x");

    mvc.perform(post("/api/v1/billing/portal").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/x"));
  }

  @Test
  void portalWhenNotEnrolledReturns409() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("e@x.com", "google", "g-409"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(issuePortalSession.execute(any(IssuePortalSessionCommand.class)))
        .thenThrow(new BillingException(BillingErrorCode.BILLING_NOT_ENROLLED));

    mvc.perform(post("/api/v1/billing/portal").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BILLING_NOT_ENROLLED"));
  }

  @Test
  void webhookOkPath() throws Exception {
    doNothing().when(handleWebhook).execute(any(SubscriptionWebhookCommand.class));

    mvc.perform(
            post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "t=1,v1=abc")
                .content("{\"id\":\"evt_test\"}"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void webhookWithInvalidSignatureReturns400() throws Exception {
    doThrow(new BillingException(BillingErrorCode.INVALID_WEBHOOK_SIGNATURE))
        .when(handleWebhook)
        .execute(any(SubscriptionWebhookCommand.class));

    mvc.perform(
            post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "t=1,v1=evil")
                .content("{\"forged\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
  }
}
