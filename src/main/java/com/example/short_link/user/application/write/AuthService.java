package com.example.short_link.user.application.write;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Instant;
import java.util.Optional;
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
  private final MobileExchangeCodeStore exchangeCodes;
  private final TwoFactorService twoFactor;
  private final JwtProperties jwtProperties;

  /**
   * Bump when the Terms/Privacy materially change so new sign-ups record the version they accepted.
   */
  private static final String TERMS_VERSION = "2026-07-21";

  /** For 2FA users, returns only a challenge until {@link #completeTwoFactor} succeeds. */
  public sealed interface TokenLoginResult {
    record Tokens(IssuedTokens issued) implements TokenLoginResult {}

    record TwoFactorRequired(String challengeToken) implements TokenLoginResult {}
  }

  /** Browser-based mobile OAuth returns a code to redeem, never a token pair. */
  public sealed interface MobileLoginResult {
    record TwoFactorRequired(String challengeToken) implements MobileLoginResult {}

    record ExchangeCode(String code) implements MobileLoginResult {}
  }

  @Transactional
  public TokenLoginResult loginWithOAuth(String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new TokenLoginResult.TwoFactorRequired(
          jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new TokenLoginResult.Tokens(issue(user));
  }

  /**
   * Returns a one-time code because the browser sheet cannot deliver tokens to the app. No session
   * is issued until {@link #exchangeMobileCode} redeems it.
   */
  @Transactional
  public MobileLoginResult loginWithOAuthMobile(
      String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new MobileLoginResult.TwoFactorRequired(
          jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new MobileLoginResult.ExchangeCode(exchangeCodes.create(user.getId()));
  }

  @Transactional
  public IssuedTokens exchangeMobileCode(String code) {
    Long userId =
        exchangeCodes
            .consume(code)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_EXCHANGE_CODE));
    return issue(loadActiveUser(userId));
  }

  private UserEntity upsertOAuthUser(String email, String oauthProvider, String oauthId) {
    UserEntity user =
        userRepository
            .findByOauthProviderAndOauthId(oauthProvider, oauthId)
            .orElseGet(
                () -> userRepository.save(newUserWithConsent(email, oauthProvider, oauthId)));
    if (user.isDeleted()) {
      log.info("restoring soft-deleted user {} on OAuth login", user.getId());
      user.restore();
    }
    return user;
  }

  /**
   * Requires subject/email verified by {@link AppleIdentityVerifier}. An existing IdP-verified
   * email links to that account, preserving one account per unique email and leaving its original
   * OAuth identity unchanged.
   */
  @Transactional
  public TokenLoginResult loginWithApple(String appleSubject, String email) {
    UserEntity user =
        userRepository
            .findByOauthProviderAndOauthId("apple", appleSubject)
            .or(() -> findLinkableByEmail(email))
            .orElseGet(() -> createAppleUser(email, appleSubject));
    if (user.isDeleted()) {
      log.info("restoring soft-deleted user {} on Apple login", user.getId());
      user.restore();
    }
    if (twoFactor.isEnabled(user.getId())) {
      return new TokenLoginResult.TwoFactorRequired(
          jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new TokenLoginResult.Tokens(issue(user));
  }

  private Optional<UserEntity> findLinkableByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(email);
  }

  private UserEntity createAppleUser(String email, String appleSubject) {
    // Apple may omit email for returning users; an unmatched account still needs one for signup.
    if (email == null || email.isBlank()) {
      throw new UserException(UserErrorCode.APPLE_EMAIL_REQUIRED);
    }
    return userRepository.save(newUserWithConsent(email, "apple", appleSubject));
  }

  private UserEntity newUserWithConsent(String email, String provider, String oauthId) {
    UserEntity user = new UserEntity(email, provider, oauthId);
    user.recordTermsConsent(TERMS_VERSION, Instant.now());
    return user;
  }

  @Transactional
  public IssuedTokens completeTwoFactor(String challengeToken, String code, boolean recovery) {
    Long userId;
    try {
      userId = jwt.parseTwoFactorChallengeToken(challengeToken);
    } catch (Exception e) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    boolean ok = recovery ? twoFactor.verifyRecovery(userId, code) : twoFactor.verify(userId, code);
    if (!ok) throw new UserException(UserErrorCode.INVALID_TOTP);
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    return issue(user);
  }

  @Transactional(readOnly = true)
  public IssuedTokens refresh(String refreshToken) {
    ParsedRefresh parsed;
    try {
      parsed = jwt.parseRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (refreshStore.exists(parsed.userId(), parsed.jti())) {
      // Keep a brief rotation marker so a shared-cookie race is not mistaken for theft.
      refreshStore.delete(parsed.userId(), parsed.jti());
      refreshStore.markRotated(parsed.userId(), parsed.jti(), jwtProperties.refreshRotationGrace());
      return issue(loadActiveUser(parsed.userId()));
    }
    if (refreshStore.wasRecentlyRotated(parsed.userId(), parsed.jti())) {
      // Tolerate shared-cookie races within the grace window by issuing a fresh pair.
      log.debug("refresh within rotation grace for userId={}, reissuing", parsed.userId());
      return issue(loadActiveUser(parsed.userId()));
    }
    // Reject only the stale or unknown token: a dropped rotation does not invalidate other live
    // sessions.
    log.warn(
        "refresh token unknown or expired for userId={}, rejecting this token "
            + "(other sessions kept)",
        parsed.userId());
    throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
  }

  private UserEntity loadActiveUser(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted()) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    return user;
  }

  public void logout(Long userId, String refreshToken) {
    logout(refreshToken);
  }

  /** Kills exactly the session whose refresh token is presented — holding the token is the auth. */
  public void logout(String refreshToken) {
    try {
      ParsedRefresh parsed = jwt.parseRefreshToken(refreshToken);
      refreshStore.delete(parsed.userId(), parsed.jti());
    } catch (Exception ignored) {
    }
  }

  private IssuedTokens issue(UserEntity user) {
    // 모든 세션 발급에서 BANNED를 거부한다. SUSPENDED는 상태 확인·소명을 위해 로그인을 허용하고
    // 콘텐츠 생성만 UserModerationGuard에서 막는다.
    if (user.isBanned()) {
      throw new UserException(UserErrorCode.ACCOUNT_BANNED);
    }
    String access = jwt.createAccessToken(user.getId(), user.getRole().name());
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl());
    return new IssuedTokens(access, refresh.token());
  }
}
