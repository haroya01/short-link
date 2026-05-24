package com.example.short_link.billing.api;

import com.example.short_link.billing.application.write.HandleSubscriptionWebhookUseCase;
import com.example.short_link.billing.application.write.IssuePortalSessionCommand;
import com.example.short_link.billing.application.write.IssuePortalSessionUseCase;
import com.example.short_link.billing.application.write.StartCheckoutCommand;
import com.example.short_link.billing.application.write.StartCheckoutUseCase;
import com.example.short_link.billing.application.write.SubscriptionWebhookCommand;
import com.example.short_link.billing.exception.BillingGatewayException;
import com.example.short_link.billing.exception.BillingNotConfiguredException;
import com.example.short_link.billing.exception.BillingNotEnrolledException;
import com.example.short_link.billing.exception.InvalidWebhookSignatureException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

  private final StartCheckoutUseCase startCheckout;
  private final IssuePortalSessionUseCase issuePortalSession;
  private final HandleSubscriptionWebhookUseCase handleWebhook;

  @PostMapping("/checkout")
  public CheckoutResponse checkout(@AuthenticationPrincipal Long userId) {
    return new CheckoutResponse(startCheckout.execute(new StartCheckoutCommand(userId)));
  }

  @PostMapping("/portal")
  public CheckoutResponse portal(@AuthenticationPrincipal Long userId) {
    return new CheckoutResponse(issuePortalSession.execute(new IssuePortalSessionCommand(userId)));
  }

  /**
   * Stripe sends raw bytes that we must verify against the configured signing secret. Spring's
   * default JSON binding would mutate the body and break the HMAC, so we read the raw stream
   * ourselves.
   */
  @PostMapping(value = "/webhook", consumes = MediaType.ALL_VALUE)
  public ResponseEntity<String> webhook(
      HttpServletRequest request, @RequestHeader("Stripe-Signature") String signature)
      throws IOException {
    String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    handleWebhook.execute(new SubscriptionWebhookCommand(payload, signature));
    return ResponseEntity.ok("ok");
  }

  @ExceptionHandler(BillingNotConfiguredException.class)
  public ResponseEntity<String> notConfigured() {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("billing not configured");
  }

  @ExceptionHandler(BillingNotEnrolledException.class)
  public ResponseEntity<String> notEnrolled() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body("not enrolled");
  }

  @ExceptionHandler(InvalidWebhookSignatureException.class)
  public ResponseEntity<String> invalidSignature() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
  }

  @ExceptionHandler(BillingGatewayException.class)
  public ResponseEntity<String> gatewayError(BillingGatewayException e) {
    log.warn("billing gateway error", e);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("billing gateway error");
  }

  public record CheckoutResponse(String url) {}
}
