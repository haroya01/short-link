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
   * Result of an OAuth login. If the user has 2FA enabled this returns only a short-lived challenge
   * token — the caller redirects the user to the 2FA prompt where they exchange the challenge +
   * TOTP code for a full pair via {@link #completeTwoFactor}.
   */
  public sealed interface LoginResult {
    record Tokens(IssuedTokens issued) implements LoginResult {}

    record TwoFactorRequired(String challengeToken) implements LoginResult {}

    record MobileExchangeCode(String code) implements LoginResult {}
  }

  @Transactional
  public LoginResult loginWithOAuth(String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new LoginResult.TwoFactorRequired(jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new LoginResult.Tokens(issue(user));
  }

  /**
   * Mobile variant of {@link #loginWithOAuth}: the browser sheet can't receive tokens directly, so
   * a successful login yields a one-time exchange code instead — the app redeems it for the pair
   * via {@link #exchangeMobileCode}. No session is issued until the code is redeemed.
   */
  @Transactional
  public LoginResult loginWithOAuthMobile(String email, String oauthProvider, String oauthId) {
    UserEntity user = upsertOAuthUser(email, oauthProvider, oauthId);
    if (twoFactor.isEnabled(user.getId())) {
      return new LoginResult.TwoFactorRequired(jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new LoginResult.MobileExchangeCode(exchangeCodes.create(user.getId()));
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
            .orElseGet(() -> userRepository.save(new UserEntity(email, oauthProvider, oauthId)));
    if (user.isDeleted()) {
      log.info("restoring soft-deleted user {} on OAuth login", user.getId());
      user.restore();
    }
    return user;
  }

  /**
   * Sign in with Apple — shared by the native app and the web "Sign in with Apple JS" flow (only
   * the delivery differs: the app reads body tokens, the web gets a refresh cookie + body access
   * token). {@link AppleIdentityVerifier} has already pinned signature/issuer/audience/nonce, so
   * subject and email arrive trusted. Linking rule: an existing account with the same email logs
   * into that account. Both Google and Apple hand us IdP-verified addresses and {@code users.email}
   * is UNIQUE, so one kurl account per email stays the invariant; the row keeps its original
   * oauth_provider/oauth_id identity and later Apple logins keep arriving through the email match.
   */
  @Transactional
  public LoginResult loginWithApple(String appleSubject, String email) {
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
      return new LoginResult.TwoFactorRequired(jwt.createTwoFactorChallengeToken(user.getId()));
    }
    return new LoginResult.Tokens(issue(user));
  }

  private Optional<UserEntity> findLinkableByEmail(String email) {
    if (email == null || email.isBlank()) {
      return Optional.empty();
    }
    return userRepository.findByEmail(email);
  }

  private UserEntity createAppleUser(String email, String appleSubject) {
    // A brand-new account needs an address; Apple always offers one (relay or real) on first
    // consent, so an absent claim here means a returning user we somehow can't match — surface it.
    if (email == null || email.isBlank()) {
      throw new UserException(UserErrorCode.APPLE_EMAIL_REQUIRED);
    }
    return userRepository.save(new UserEntity(email, "apple", appleSubject));
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
      // Live session: rotate it (one-time use) and leave a short grace marker, so a stale replay of
      // this same token from another tab/subdomain isn't mistaken for theft.
      refreshStore.delete(parsed.userId(), parsed.jti());
      refreshStore.markRotated(parsed.userId(), parsed.jti(), jwtProperties.refreshRotationGrace());
      return issue(loadActiveUser(parsed.userId()));
    }
    if (refreshStore.wasRecentlyRotated(parsed.userId(), parsed.jti())) {
      // Replay inside the grace window — the benign cross-tab / cross-subdomain race. Re-issue a
      // fresh pair instead of wiping the user's other (still valid) sessions.
      log.debug("refresh within rotation grace for userId={}, reissuing", parsed.userId());
      return issue(loadActiveUser(parsed.userId()));
    }
    // Unknown jti past its grace window: most often a stale token the client never advanced to (a
    // dropped rotation, an idle tab), occasionally a replayed/stolen old one. Reject just THIS
    // token
    // — nuking every session over a single stale token logged the owner out on all devices (the
    // "logged out whenever I step away" report), and a rotated token is already worthless, so the
    // blast radius bought little. Other live sessions stay intact.
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
    String access = jwt.createAccessToken(user.getId(), user.getRole().name());
    RefreshToken refresh = jwt.createRefreshToken(user.getId());
    refreshStore.save(user.getId(), refresh.jti(), jwt.refreshTtl());
    return new IssuedTokens(access, refresh.token());
  }
}
