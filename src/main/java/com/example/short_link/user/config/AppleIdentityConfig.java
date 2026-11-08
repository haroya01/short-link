package com.example.short_link.user.config;

import com.example.short_link.user.application.properties.AppleSignInProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
public class AppleIdentityConfig {

  @Bean
  JwtDecoder appleIdentityDecoder(AppleSignInProperties properties) {
    return decoder(properties, Duration.ofSeconds(3), Duration.ofSeconds(5));
  }

  static JwtDecoder decoder(AppleSignInProperties properties, Duration connect, Duration read) {
    SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
    http.setConnectTimeout(connect);
    http.setReadTimeout(read);
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
            .restOperations(new RestTemplate(http))
            .build();
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
