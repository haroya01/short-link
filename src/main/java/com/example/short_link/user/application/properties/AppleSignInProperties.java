package com.example.short_link.user.application.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Native Sign in with Apple verification knobs. {@code clientIds} are the audiences we accept in
 * the identity token — for the native flow that is the iOS bundle id, not a Services ID. Defaults
 * cover production so the test profile needs no overrides.
 */
@ConfigurationProperties(prefix = "short-link.apple")
public record AppleSignInProperties(String issuer, String jwkSetUri, List<String> clientIds) {

  public AppleSignInProperties {
    if (issuer == null || issuer.isBlank()) issuer = "https://appleid.apple.com";
    if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = "https://appleid.apple.com/auth/keys";
    if (clientIds == null || clientIds.isEmpty()) clientIds = List.of("focustime.kurl");
  }
}
