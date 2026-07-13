package com.example.short_link.user.application;

import com.example.short_link.common.security.ClickStreamTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class JwtClickStreamTokenService implements ClickStreamTokenService {

  private final JwtTokenService jwt;

  @Override
  public Long parseStreamToken(String token, String shortCode) {
    return jwt.parseStreamToken(token, shortCode);
  }

  @Override
  public String createStreamToken(Long userId, String shortCode) {
    return jwt.createStreamToken(userId, shortCode);
  }
}
