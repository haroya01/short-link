package com.example.short_link.common.security;

public interface ClickStreamTokenService {

  Long parseStreamToken(String token, String shortCode);

  Long parseLegacyAccessToken(String token);

  String createStreamToken(Long userId, String shortCode);
}
