package com.example.short_link.user.application.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code clientIds} lists accepted token audiences: native bundle IDs and, for web login, the
 * Services ID.
 */
@ConfigurationProperties(prefix = "short-link.apple")
public record AppleSignInProperties(String issuer, String jwkSetUri, List<String> clientIds) {

  public AppleSignInProperties {
    if (issuer == null || issuer.isBlank()) issuer = "https://appleid.apple.com";
    if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = "https://appleid.apple.com/auth/keys";
    if (clientIds == null || clientIds.isEmpty())
      clientIds = List.of("focustime.kurl", "focustime.kurl.links");
  }
}
