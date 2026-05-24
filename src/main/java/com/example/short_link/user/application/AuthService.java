package com.example.short_link.user.application;

import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.RefreshTokenStore;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.exception.InvalidRefreshTokenException;
import com.example.short_link.user.exception.InvalidTotpCodeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final JwtTokenService jwt;
  private final RefreshTokenStore refreshStore;
  private final TwoFactorService twoFactor;

  /**
   * Result of an OAuth login. If the user has 2FA enabled this returns only a short-lived challenge
   * token — the caller redirects the user to the 2FA prompt where they exchange the challenge +
   * TOTP code for a full pair via {@link #completeTwoFactor}.
   */
  public sealed interface LoginResult {
    record Tokens(IssuedTokens issued) implements LoginResult {}

    record TwoFactorRequired(String challengeToken) implements LoginResult {}
  }

  @Transactional
  public LoginResult loginWithOAuth(String email, String oauthProvider, String oauthId) {
    UserEntity user =
        userRepository
            .findByOauthProviderAndOauthId(oauthProvider, oauthId)
            .orElseGet(() -> userRepository.save(new UserEntity(email, oauthProvider, oauthId)));
    if (user.isDeleted()) {
      log.info("restoring soft-deleted user {} on OAuth login", user.getId());
      user.restore();
    }
    if (twoFactor.isEnabled(user.getId())) {
      return new LoginResult.TwoFactorRequired(jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new LoginResult.Tokens(issue(user));
  }

  @Transactional
  public IssuedTokens completeTwoFactor(String challengeToken, String code, boolean recovery) {
    Long userId;
    try {
      userId = jwt.parseTwoFactorChallengeToken(challengeToken);
    } catch (Exception e) {
      throw new InvalidRefreshTokenException();
    }
    boolean ok = recovery ? twoFactor.verifyRecovery(userId, code) : twoFactor.verify(userId, code);
    if (!ok) throw new InvalidTotpCodeException();
    UserEntity user =
        userRepository.findById(userId).orElseThrow(InvalidRefreshTokenException::new);
    if (user.isDeleted()) throw new InvalidRefreshTokenException();
    return issue(user);
  }

  @Transactional(readOnly = true)
  public IssuedTokens refresh(String refreshToken) {
    ParsedRefresh parsed;
    try {
      parsed = jwt.parseRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new InvalidRefreshTokenException();
    }
    if (!refreshStore.exists(parsed.userId(), parsed.jti())) {
      log.warn(
          "refresh token reuse or theft suspected for userId={}, wiping all sessions",
          parsed.userId());
      refreshStore.deleteAllForUser(parsed.userId());
      throw new InvalidRefreshTokenException();
    }
    refreshStore.delete(parsed.userId(), parsed.jti());
    UserEntity user =
        userRepository.findById(parsed.userId()).orElseThrow(InvalidRefreshTokenException::new);
    if (user.isDeleted()) {
      throw new InvalidRefreshTokenException();
    }
    return issue(user);
  }

  public void logout(Long userId, String refreshToken) {
    try {
      ParsedRefresh parsed = jwt.parseRefreshToken(refreshToken);
      refreshStore.delete(parsed.userId(), parsed.jti());
    } catch (Exception ignored) {
    }
  }

  private IssuedTokens issue(UserEntity user) {
    String access = jwt.createAccessToken(user.getId(), user.getRole().name());
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl());
    return new IssuedTokens(access, refresh.token());
  }
}
