package com.example.short_link.user.application.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// clientIds lists accepted token audiences: native bundle IDs and, for web login, the Services ID.
// An empty APPLE_SIGNIN_CLIENT_IDS binds as an empty list, not the application.yml default, so the
// fallback must stay the same full set as application.yml and deploy/docker-compose.yml.
@ConfigurationProperties(prefix = "short-link.apple")
public record AppleSignInProperties(String issuer, String jwkSetUri, List<String> clientIds) {

  public AppleSignInProperties {
    if (issuer == null || issuer.isBlank()) issuer = "https://appleid.apple.com";
    if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = "https://appleid.apple.com/auth/keys";
    if (clientIds == null || clientIds.isEmpty())
      clientIds = List.of("focustime.kurl", "focustime.kurl.links", "me.kurl.signin");
  }
}
