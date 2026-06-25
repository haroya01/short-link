package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.DeviceTokenCommandService;
import com.example.short_link.user.presentation.request.RegisterDeviceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 푸시 디바이스 등록 — 앱이 APNs 토큰을 받을 때마다 upsert, 로그아웃 때 삭제. */
@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

  private final DeviceTokenCommandService deviceTokens;

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void register(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterDeviceRequest request) {
    deviceTokens.register(userId, request.token(), request.platform());
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterDeviceRequest request) {
    deviceTokens.unregister(userId, request.token());
  }
}
