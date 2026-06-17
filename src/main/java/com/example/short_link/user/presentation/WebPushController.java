package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.WebPushSubscriptionCommandService;
import com.example.short_link.user.presentation.request.SubscribeWebPushRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저 웹푸시 구독 — 권한 허용 시 구독 등록(upsert), 끌 때/로그아웃 때 삭제. */
@RestController
@RequestMapping("/api/v1/notifications/web-push")
@RequiredArgsConstructor
public class WebPushController {

  private final WebPushSubscriptionCommandService subscriptions;

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void subscribe(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody SubscribeWebPushRequest request) {
    subscriptions.subscribe(userId, request.endpoint(), request.p256dh(), request.auth());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unsubscribe(@RequestParam String endpoint) {
    subscriptions.unsubscribe(endpoint);
  }
}
