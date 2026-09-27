package com.example.short_link.user.config;

import com.example.short_link.user.application.properties.AppleSignInProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
public class AppleIdentityConfig {

  @Bean
  JwtDecoder appleIdentityDecoder(AppleSignInProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    decoder.setJwtValidator(acceptedClaims(properties));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> acceptedClaims(AppleSignInProperties properties) {
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().stream().anyMatch(properties.clientIds()::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                        "invalid_token", "audience is not an accepted client id", null));
    // Replacing Nimbus' validator chain also replaces its default expiration check.
    return new DelegatingOAuth2TokenValidator<>(
        new JwtTimestampValidator(), new JwtIssuerValidator(properties.issuer()), audience);
  }
}
