package com.example.short_link.notification.application.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID keys use Base64URL and must be supplied through environment variables. Missing keys disable
 * sending. The public key must match the frontend subscription key; subject is a mailto: or site
 * URL.
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
