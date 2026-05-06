package com.example.short_link.user.application;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final JwtTokenService jwt;
  private final RefreshTokenStore refreshStore;

  @Transactional
  public IssuedTokens loginWithOAuth(String email, String oauthProvider, String oauthId) {
    UserEntity user =
        userRepository
            .findByOauthProviderAndOauthId(oauthProvider, oauthId)
            .orElseGet(() -> userRepository.save(new UserEntity(email, oauthProvider, oauthId)));
    return issue(user.getId());
  }

  public IssuedTokens refresh(String refreshToken) {
    ParsedRefresh parsed;
    try {
      parsed = jwt.parseRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new InvalidRefreshTokenException();
    }
    if (!refreshStore.exists(parsed.userId(), parsed.jti())) {
      throw new InvalidRefreshTokenException();
    }
    refreshStore.delete(parsed.userId(), parsed.jti());
    return issue(parsed.userId());
  }

  public void logout(Long userId, String refreshToken) {
    try {
      ParsedRefresh parsed = jwt.parseRefreshToken(refreshToken);
      refreshStore.delete(parsed.userId(), parsed.jti());
    } catch (Exception ignored) {
    }
  }

  private IssuedTokens issue(Long userId) {
    String access = jwt.createAccessToken(userId);
    RefreshToken refresh = jwt.createRefreshToken(userId);
    refreshStore.save(userId, refresh.jti(), jwt.refreshTtl());
    return new IssuedTokens(access, refresh.token());
  }
}
