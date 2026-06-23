package com.example.short_link.notification.presentation;

import com.example.short_link.notification.application.link.NotificationPreferenceService;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.presentation.request.UpdateNotificationPreferenceRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 링크 알림 종류별 켜고끄기 — 앱 설정 화면이 읽고(GET) 토글한다(PUT). 행 없으면 켜짐(기본 on). */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

  private final NotificationPreferenceService service;

  /** type → enabled 전체 맵(모든 종류, 기본 true). */
  @GetMapping
  public Map<LinkNotificationType, Boolean> list(@AuthenticationPrincipal Long userId) {
    return service.all(userId);
  }

  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void update(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
    service.setEnabled(userId, request.type(), request.enabled());
  }
}
