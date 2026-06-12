package com.example.short_link.user.presentation;

import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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

  private final DeviceTokenRepository deviceTokens;

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void register(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterDeviceRequest request) {
    deviceTokens
        .findByToken(request.token())
        .ifPresentOrElse(
            existing -> existing.reassign(userId),
            () ->
                deviceTokens.save(
                    new DeviceTokenEntity(userId, request.token(), request.platform())));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unregister(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RegisterDeviceRequest request) {
    // 소유자 검증 없이 토큰만으로 지운다 — 토큰을 아는 쪽이 그 기기다(로그아웃 직후 호출).
    deviceTokens.deleteByToken(request.token());
  }

  public record RegisterDeviceRequest(
      @NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 16) String platform) {}
}
