package com.example.short_link.notification.application.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹푸시(VAPID) 발송 설정. {@code publicKey}/{@code privateKey} 는 VAPID 키페어(Base64URL), {@code subject} 는
 * mailto: 또는 사이트 URL. 비어 있으면 {@link
 * com.example.short_link.notification.infrastructure.WebPushSender} 는 no-op 으로 내려앉고 앱은 평소처럼 돈다(키는
 * 운영 환경변수로만 — APNs 의 .p8 과 같은 취급). 공개키는 프론트(Service Worker 구독)와 같은 값이어야 한다.
 */
@ConfigurationProperties(prefix = "short-link.web-push")
public record VapidProperties(String publicKey, String privateKey, String subject) {

  public VapidProperties {
    if (subject == null || subject.isBlank()) subject = "mailto:privacy@kurl.me";
  }

  public boolean configured() {
    return notBlank(publicKey) && notBlank(privateKey);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
