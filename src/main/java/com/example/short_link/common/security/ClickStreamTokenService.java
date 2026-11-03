package com.example.short_link.common.security;

import java.time.Duration;

public interface ClickStreamTokenService {

  Long parseStreamToken(String token, String shortCode);

  String createStreamToken(Long userId, String shortCode);

  String createStreamToken(Long userId, String scope, Duration ttl);
}
