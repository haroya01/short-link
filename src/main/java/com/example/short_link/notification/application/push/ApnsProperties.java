package com.example.short_link.notification.application.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code privateKey}는 APNs .p8 PEM 원문이며 운영 환경변수로만 제공한다. 비어 있으면 발송하지 않는다. 개발 서명 앱은 기본 sandbox 게이트웨이,
 * 스토어 빌드는 {@code production=true}를 사용한다.
 */
@ConfigurationProperties(prefix = "short-link.apns")
public record ApnsProperties(
    String teamId,
    String keyId,
    String bundleId,
    String privateKey,
    boolean production,
    String linksBundleId) {

  public ApnsProperties {
    if (bundleId == null || bundleId.isBlank()) bundleId = "focustime.kurl";
    if (linksBundleId == null || linksBundleId.isBlank()) linksBundleId = "focustime.kurl.links";
  }

  public String topicFor(PushApp app) {
    return app == PushApp.LINKS ? linksBundleId : bundleId;
  }

  public String otherTopic(String topic) {
    return bundleId.equals(topic) ? linksBundleId : bundleId;
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
