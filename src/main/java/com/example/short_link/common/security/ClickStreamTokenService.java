package com.example.short_link.common.security;

public interface ClickStreamTokenService {

  Long parseStreamToken(String token, String shortCode);

  String createStreamToken(Long userId, String shortCode);
}
