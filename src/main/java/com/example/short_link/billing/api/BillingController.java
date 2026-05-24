package com.example.short_link.billing.api;

import com.example.short_link.billing.application.write.HandleSubscriptionWebhookUseCase;
import com.example.short_link.billing.application.write.IssuePortalSessionCommand;
import com.example.short_link.billing.application.write.IssuePortalSessionUseCase;
import com.example.short_link.billing.application.write.StartCheckoutCommand;
import com.example.short_link.billing.application.write.StartCheckoutUseCase;
import com.example.short_link.billing.application.write.SubscriptionWebhookCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  public record CheckoutResponse(String url) {}
}
