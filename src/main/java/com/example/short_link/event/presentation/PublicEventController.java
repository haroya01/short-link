package com.example.short_link.event.presentation;

import com.example.short_link.common.pow.PowRequiredException;
import com.example.short_link.common.pow.PowService;
import com.example.short_link.common.web.ClientIp;
import com.example.short_link.event.application.read.PublicEventQueryService;
import com.example.short_link.event.application.read.PublicEventView;
import com.example.short_link.event.application.write.CancelRegistrationUseCase;
import com.example.short_link.event.application.write.RegisterForEventCommand;
import com.example.short_link.event.application.write.RegisterForEventUseCase;
import com.example.short_link.event.application.write.RegistrationResult;
import com.example.short_link.event.presentation.request.CancelRegistrationRequest;
import com.example.short_link.event.presentation.request.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 참가자 표면 — 읽기와 신청/취소 전부 비로그인. 신청은 익명 단축과 같은 PoW 게이트. */
@RestController
@RequestMapping("/api/v1/public/events")
@RequiredArgsConstructor
public class PublicEventController {

  private final PublicEventQueryService publicEventQueryService;
  private final RegisterForEventUseCase registerForEvent;
  private final CancelRegistrationUseCase cancelRegistration;
  private final PowService powService;

  @GetMapping("/{slug}")
  public PublicEventView find(@PathVariable String slug) {
    return publicEventQueryService.findBySlug(slug);
  }

  @PostMapping("/{slug}/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  public RegistrationResult register(
      @AuthenticationPrincipal Long userId,
      @PathVariable String slug,
      @Valid @RequestBody RegisterRequest request,
      @RequestHeader(value = "X-Pow-Challenge", required = false) String powChallenge,
      @RequestHeader(value = "X-Pow-Nonce", required = false) String powNonce,
      @RequestHeader(value = "User-Agent", required = false) String userAgent,
      HttpServletRequest req) {
    if (userId == null && powService.isEnforced()) {
      if (!powService.verifyAndConsume(powChallenge, powNonce)) {
        throw new PowRequiredException();
      }
    }
    return registerForEvent.execute(
        new RegisterForEventCommand(
            slug,
            request.name(),
            request.contact(),
            request.answers(),
            ClientIp.of(req),
            userAgent));
  }

  @PostMapping("/registrations/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(@Valid @RequestBody CancelRegistrationRequest request) {
    cancelRegistration.execute(request.token());
  }
}
