package com.example.short_link.notification.application.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APNs 발송 설정. {@code privateKey} 는 Apple 개발자 포털의 APNs Auth Key(.p8) PEM 원문 — 비어 있으면 발송기는 no-op 으로
 * 내려앉고 앱은 평소처럼 돈다(키는 운영 환경변수로만). {@code production} 기본 false: 개발 서명 앱(aps-environment=development)은
 * sandbox 게이트웨이를 쓴다. 스토어 빌드 배포 시점에 true 로.
 */
@ConfigurationProperties(prefix = "short-link.apns")
public record ApnsProperties(
    String teamId, String keyId, String bundleId, String privateKey, boolean production) {

  public ApnsProperties {
    if (bundleId == null || bundleId.isBlank()) bundleId = "focustime.kurl";
  }

  public boolean configured() {
    return notBlank(teamId) && notBlank(keyId) && notBlank(privateKey);
  }

  public String host() {
    return production ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
