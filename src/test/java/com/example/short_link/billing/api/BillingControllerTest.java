package com.example.short_link.billing.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.billing.application.BillingNotConfiguredException;
import com.example.short_link.billing.application.BillingNotEnrolledException;
import com.example.short_link.billing.application.BillingService;
import com.example.short_link.billing.application.InvalidWebhookSignatureException;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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

  @MockitoBean private BillingService billing;

  @Test
  void anonymousCheckoutIs401() throws Exception {
    mvc.perform(post("/api/v1/billing/checkout")).andExpect(status().isUnauthorized());
  }

  @Test
  void checkoutReturnsUrl() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-co"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(billing.createCheckoutSession(eq(user.getId())))
        .thenReturn("https://checkout.stripe.com/c/x");

    mvc.perform(post("/api/v1/billing/checkout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/x"));
  }

  @Test
  void checkoutWhenNotConfiguredReturns503() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-503"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(billing.createCheckoutSession(eq(user.getId())))
        .thenThrow(new BillingNotConfiguredException());

    mvc.perform(post("/api/v1/billing/checkout").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string("billing not configured"));
  }

  @Test
  void portalReturnsUrl() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-po"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(billing.createPortalSession(eq(user.getId())))
        .thenReturn("https://billing.stripe.com/p/x");

    mvc.perform(post("/api/v1/billing/portal").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/x"));
  }

  @Test
  void portalWhenNotEnrolledReturns409() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("e@x.com", "google", "g-409"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(billing.createPortalSession(eq(user.getId())))
        .thenThrow(new BillingNotEnrolledException());

    mvc.perform(post("/api/v1/billing/portal").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict())
        .andExpect(content().string("not enrolled"));
  }

  @Test
  void webhookOkPath() throws Exception {
    doNothing().when(billing).handleWebhook(anyString(), anyString());

    mvc.perform(
            post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "t=1,v1=abc")
                .content("{\"id\":\"evt_test\"}"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void webhookWithInvalidSignatureReturns400() throws Exception {
    doThrow(new InvalidWebhookSignatureException())
        .when(billing)
        .handleWebhook(anyString(), anyString());

    mvc.perform(
            post("/api/v1/billing/webhook")
                .header("Stripe-Signature", "t=1,v1=evil")
                .content("{\"forged\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("invalid signature"));
  }
}
