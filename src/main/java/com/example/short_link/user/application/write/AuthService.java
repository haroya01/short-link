package com.example.short_link.user.application.write;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.SessionChannel;
import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.dto.ParsedRefresh;
import com.example.short_link.user.application.dto.TwoFactorChallenge;
import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.application.twofactor.TwoFactorService;
import com.example.short_link.user.domain.RefreshToken;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
  private final DeviceTokenRepository deviceTokens;
  private final Clock clock;

  // Bump when the Terms/Privacy materially change so new sign-ups record the version they accepted.
  private static final String TERMS_VERSION = "2026-10-13";

  public sealed interface TokenLoginResult {
    record Tokens(IssuedTokens issued) implements TokenLoginResult {}

    record TwoFactorRequired(String challengeToken) implements TokenLoginResult {}
  }

  public sealed interface MobileLoginResult {
    record TwoFactorRequired(String challengeToken) implements MobileLoginResult {}

    record ExchangeCode(String code) implements MobileLoginResult {}
  }

  @Transactional
  public TokenLoginResult loginWithOAuth(String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new TokenLoginResult.TwoFactorRequired(challengeFor(user));
    }
    restoreIfDeleted(user);
    return new TokenLoginResult.Tokens(issue(user, SessionChannel.WEB));
  }

  // Returns a one-time code because the browser sheet cannot deliver tokens to the app. No session
  // is issued until exchangeMobileCode redeems it.
  @Transactional
  public MobileLoginResult loginWithOAuthMobile(
      String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new MobileLoginResult.TwoFactorRequired(challengeFor(user));
    }
    restoreIfDeleted(user);
    return new MobileLoginResult.ExchangeCode(exchangeCodes.create(user.getId()));
  }

  @Transactional
  public IssuedTokens exchangeMobileCode(String code) {
    Long userId =
        exchangeCodes
            .consume(code)
            .orElseThrow(() -> new UserException(UserErrorCode.INVALID_EXCHANGE_CODE));
    return issue(loadActiveUser(userId), SessionChannel.MOBILE);
  }

  private UserEntity upsertOAuthUser(String email, String oauthProvider, String oauthId) {
    return userRepository
        .findByOauthProviderAndOauthId(oauthProvider, oauthId)
        .orElseGet(() -> userRepository.save(newUserWithConsent(email, oauthProvider, oauthId)));
  }

  private String challengeFor(UserEntity user) {
    return jwt.createTwoFactorChallengeToken(user.getId(), user.isDeleted());
  }

  private static void restoreIfDeleted(UserEntity user) {
    if (user.isDeleted()) {
      log.info("restoring soft-deleted user {} on login", user.getId());
      user.restore();
    }
  }

  // Requires subject/email verified by AppleIdentityVerifier. An existing IdP-verified email links
  // to that account, preserving one account per unique email and leaving its original OAuth
  // identity unchanged.
  @Transactional
  public TokenLoginResult loginWithApple(
      String appleSubject, String email, SessionChannel channel) {
    UserEntity user =
        userRepository
            .findByOauthProviderAndOauthId("apple", appleSubject)
            .or(() -> findLinkableByEmail(email))
            .orElseGet(() -> createAppleUser(email, appleSubject));
    if (twoFactor.isEnabled(user.getId())) {
      return new TokenLoginResult.TwoFactorRequired(challengeFor(user));
    }
    restoreIfDeleted(user);
    return new TokenLoginResult.Tokens(issue(user, channel));
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
  public IssuedTokens completeTwoFactor(
      String challengeToken, String code, boolean recovery, SessionChannel channel) {
    TwoFactorChallenge challenge;
    try {
      challenge = jwt.parseTwoFactorChallenge(challengeToken);
    } catch (Exception e) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    Long userId = challenge.userId();
    boolean ok = recovery ? twoFactor.verifyRecovery(userId, code) : twoFactor.verify(userId, code);
    if (!ok) throw new UserException(UserErrorCode.INVALID_TOTP);
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (user.isDeleted() && !challenge.restoresAccount()) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    restoreIfDeleted(user);
    return issue(user, channel);
  }

  @Transactional
  public IssuedTokens refresh(String refreshToken, SessionChannel channel) {
    ParsedRefresh parsed;
    try {
      parsed = jwt.parseRefreshToken(refreshToken);
    } catch (Exception e) {
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    if (channel == SessionChannel.MOBILE && parsed.webOnly()) {
      log.warn("web refresh token sent to the app refresh for userId={}", parsed.userId());
      throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
    }
    // Only the request that consumed the token rotates it. The marker left behind keeps a
    // shared-cookie race from being mistaken for theft.
    if (refreshStore.consume(parsed.userId(), parsed.jti(), jwtProperties.refreshRotationGrace())) {
      return continueSession(parsed, channel);
    }
    if (withinRotationGrace(parsed)) {
      log.debug("refresh within rotation grace for userId={}, reissuing", parsed.userId());
      return continueSession(parsed, channel);
    }
    log.warn(
        "refresh token unknown or expired for userId={}, rejecting this token "
            + "(other sessions kept)",
        parsed.userId());
    throw new UserException(UserErrorCode.INVALID_REFRESH_TOKEN);
  }

  private boolean withinRotationGrace(ParsedRefresh parsed) {
    if (!refreshStore.wasRecentlyRotated(parsed.userId(), parsed.jti())) return false;
    return parsed.sessionId() == null
        || !refreshStore.sessionEnded(parsed.userId(), parsed.sessionId());
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

  // Logging out ends the session, and with it the pushes to the devices it registered.
  public void logout(String refreshToken) {
    try {
      ParsedRefresh parsed = jwt.parseRefreshToken(refreshToken);
      refreshStore.delete(parsed.userId(), parsed.jti());
      if (parsed.sessionId() != null) {
        refreshStore.endSession(
            parsed.userId(), parsed.sessionId(), jwtProperties.refreshRotationGrace());
        deviceTokens.endSession(parsed.userId(), parsed.sessionId());
      }
    } catch (Exception ignored) {
    }
  }

  // A rotation keeps the session id (a refresh token from before sessions starts one) and moves the
  // session's devices' end along with the new refresh token's.
  private IssuedTokens continueSession(ParsedRefresh parsed, SessionChannel channel) {
    String sessionId =
        parsed.sessionId() != null ? parsed.sessionId() : UUID.randomUUID().toString();
    IssuedTokens tokens = issue(loadActiveUser(parsed.userId()), sessionId, channel);
    deviceTokens.extendSession(
        parsed.userId(), sessionId, clock.instant().plus(jwt.refreshTtl(channel)));
    return tokens;
  }

  private IssuedTokens issue(UserEntity user, SessionChannel channel) {
    return issue(user, UUID.randomUUID().toString(), channel);
  }

  private IssuedTokens issue(UserEntity user, String sessionId, SessionChannel channel) {
    // 모든 세션 발급에서 BANNED를 거부한다. SUSPENDED는 상태 확인·소명을 위해 로그인을 허용하고
    // 콘텐츠 생성만 UserModerationGuard에서 막는다.
    if (user.isBanned()) {
      throw new UserException(UserErrorCode.ACCOUNT_BANNED);
    }
    String access = jwt.createAccessToken(user.getId(), user.getRole().name(), sessionId);
    RefreshToken refresh = jwt.createRefreshToken(user.getId(), sessionId, channel);
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl(channel));
    return new IssuedTokens(access, refresh.token(), user.isAdmin());
  }
}
