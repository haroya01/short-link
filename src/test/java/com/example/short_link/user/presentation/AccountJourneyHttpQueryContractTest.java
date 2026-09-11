package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.testsupport.AccountHttpJourneySupport;
import com.example.short_link.user.application.dto.AppleIdentity;
import com.example.short_link.user.application.twofactor.TotpCodec;
import com.example.short_link.user.application.write.AppleIdentityVerifier;
import com.example.short_link.user.application.write.MobileExchangeCodeStore;
import com.example.short_link.user.application.write.RefreshTokenStore;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Only Apple identity verification and object-provider transport are substituted. */
@TestPropertySource(
    properties = {
      "short-link.twofa.aes-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "short-link.avatar.public-base-url=https://cdn.example.test"
    })
class AccountJourneyHttpQueryContractTest extends AccountHttpJourneySupport {
  @MockitoBean private AppleIdentityVerifier apple;
  @MockitoBean private ObjectStorage storage;
  @Autowired private RefreshTokenStore refreshTokens;
  @Autowired private MobileExchangeCodeStore exchangeCodes;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;

  @BeforeEach
  void configureExternalProviderBoundaries() {
    when(apple.verify(anyString(), anyString()))
        .thenReturn(new AppleIdentity("apple-" + owner.getId(), owner.getEmail()));
    when(storage.isConfigured()).thenReturn(true);
    when(storage.objectSize(anyString())).thenReturn(Optional.of(1024L));
    when(storage.presignPut(anyString(), anyString(), any()))
        .thenAnswer(call -> "https://upload.example.test/" + call.getArgument(0));
  }

  @Test
  void editsAccountPreferencesExportsDataAndSoftDeletesAccount() throws Exception {
    ExportFixture fixture = commitOwnedAndForeignExportData();
    assertThat(
            body(call("account-me-read", "GET", "/api/v1/users/me", null, token, 200))
                .path("email")
                .asText())
        .isEqualTo(owner.getEmail());
    call(
        "account-preferences-update",
        "PUT",
        "/api/v1/users/me/preferences",
        Map.of("timezone", "Asia/Tokyo"),
        token,
        200);
    assertThat(
            jdbc.queryForObject(
                "select timezone from users where id=?", String.class, owner.getId()))
        .isEqualTo("Asia/Tokyo");
    var exported = call("account-data-export", "GET", "/api/v1/users/me/export", null, token, 200);
    assertThat(exported.body())
        .contains(owner.getEmail())
        .doesNotContain(
            stranger.getEmail(),
            fixture.foreignLink().getOriginalUrl(),
            "private-referrer.example.com");
    var exportedLinks = body(exported).path("links");
    assertThat(exportedLinks.size()).isEqualTo(1);
    assertThat(exportedLinks.get(0).path("shortCode").asText())
        .isEqualTo(fixture.ownedLink().getShortCode().value());
    assertThat(exportedLinks.get(0).path("originalUrl").asText())
        .isEqualTo(fixture.ownedLink().getOriginalUrl());
    var exportedClicks = body(exported).path("clickEvents");
    assertThat(exportedClicks.size()).isEqualTo(1);
    assertThat(exportedClicks.get(0).path("shortCode").asText())
        .isEqualTo(fixture.ownedLink().getShortCode().value());
    assertThat(exportedClicks.get(0).path("countryCode").asText()).isEqualTo("JP");
    assertThat(exportedClicks.get(0).path("referrerHost").asText())
        .isEqualTo("owned-referrer.example.com");
    assertThat(exportedClicks.get(0).path("clickedAt").asText()).isEqualTo("2026-01-15T00:00:00Z");
    assertThat(exported.headers().firstValue("Content-Disposition")).isPresent();
    var refresh = jwt.createRefreshToken(owner.getId());
    refreshTokens.save(owner.getId(), refresh.jti(), jwt.refreshTtl());
    call("account-delete", "DELETE", "/api/v1/users/me", null, token, 204);
    assertThat(
            count(
                "select count(*) from users where id=? and deleted_at is not null", owner.getId()))
        .isEqualTo(1);
    assertThat(refreshTokens.exists(owner.getId(), refresh.jti())).isFalse();
    // Account deletion is a soft delete: links and click history are retained by this API.
    assertThat(
            count(
                "select count(*) from link where id=? and user_id=?",
                fixture.ownedLink().getId(),
                owner.getId()))
        .isEqualTo(1);
    assertThat(count("select count(*) from click_event where id=?", fixture.ownedClickId()))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from link where id=? and user_id=?",
                fixture.foreignLink().getId(),
                stranger.getId()))
        .isEqualTo(1);
    assertThat(count("select count(*) from click_event where id=?", fixture.foreignClickId()))
        .isEqualTo(1);
    call("account-deleted-me-rejected", "GET", "/api/v1/users/me", null, token, 404);
    assertThat(
            count("select count(*) from users where id=? and deleted_at is null", stranger.getId()))
        .isEqualTo(1);
  }

  @Test
  void issuesUsesAndRevokesAnApiKeyWithoutExposingItsStoredHash() throws Exception {
    String path = "/api/v1/users/me/api-keys";
    var issued =
        body(call("account-api-key-issue", "POST", path, Map.of("name", "automation"), token, 201));
    long id = issued.path("id").asLong();
    String raw = issued.path("rawKey").asText();
    assertThat(raw).startsWith("kurl_");
    assertThat(jdbc.queryForObject("select key_hash from api_key where id=?", String.class, id))
        .hasSize(64)
        .isNotEqualTo(raw);
    var listed = call("account-api-key-list", "GET", path, null, token, 200);
    assertThat(body(listed).get(0).path("id").asLong()).isEqualTo(id);
    assertThat(listed.body()).doesNotContain(raw, "keyHash");
    assertThat(
            body(call("account-api-key-authenticate", "GET", "/api/v1/users/me", null, raw, 200))
                .path("id")
                .asLong())
        .isEqualTo(owner.getId());
    assertThat(count("select count(*) from api_key where id=? and last_used_at is not null", id))
        .isEqualTo(1);
    call("account-api-key-cross-user-revoke", "DELETE", path + "/" + id, null, strangerToken, 404);
    assertThat(count("select count(*) from api_key where id=? and revoked_at is null", id))
        .isEqualTo(1);
    call("account-api-key-revoke", "DELETE", path + "/" + id, null, token, 204);
    assertThat(count("select count(*) from api_key where id=? and revoked_at is not null", id))
        .isEqualTo(1);
    call("account-api-key-revoked-rejected", "GET", "/api/v1/users/me", null, raw, 401);
  }

  @Test
  void webLoginCreatesUserRotatesCookieAndLogsOutRealRedisSession() throws Exception {
    String email = "dev-" + owner.getUsername() + "@example.com";
    var login =
        call(
            "account-dev-login",
            "POST",
            "/api/v1/auth/dev-login",
            Map.of("email", email),
            null,
            200);
    assertThat(count("select count(*) from users where email=? and oauth_provider='dev'", email))
        .isEqualTo(1);
    String cookie = refreshCookie(login);
    var previous = jwt.parseRefreshToken(cookie.substring("refresh_token=".length()));
    assertThat(refreshTokens.exists(previous.userId(), previous.jti())).isTrue();
    var refreshed =
        callWithHeaders(
            "account-web-refresh",
            "POST",
            "/api/v1/auth/refresh",
            null,
            Map.of("Cookie", cookie),
            200);
    String rotatedCookie = refreshCookie(refreshed);
    var rotated = jwt.parseRefreshToken(rotatedCookie.substring("refresh_token=".length()));
    assertThat(rotated.jti()).isNotEqualTo(previous.jti());
    assertThat(refreshTokens.exists(previous.userId(), previous.jti())).isFalse();
    assertThat(refreshTokens.exists(rotated.userId(), rotated.jti())).isTrue();
    callWithHeaders(
        "account-web-logout",
        "POST",
        "/api/v1/auth/logout",
        null,
        Map.of(
            "Cookie",
            rotatedCookie,
            "Authorization",
            "Bearer " + body(refreshed).path("accessToken").asText()),
        200);
    assertThat(refreshTokens.exists(rotated.userId(), rotated.jti())).isFalse();
    callWithHeaders(
        "account-web-logged-out-refresh-rejected",
        "POST",
        "/api/v1/auth/refresh",
        null,
        Map.of("Cookie", rotatedCookie),
        401);
  }

  @Test
  void mobileLoginExchangesOneTimeCodeRotatesTokenAndLogsOut() throws Exception {
    var start = call("account-mobile-start", "GET", "/api/v1/auth/mobile/start", null, null, 302);
    assertThat(start.headers().firstValue("Location").orElseThrow())
        .endsWith("/oauth2/authorization/google");
    // External OAuth callback's one-time exchange code is fixture data in the real Redis adapter.
    String code = exchangeCodes.create(owner.getId());
    var exchanged =
        body(
            call(
                "account-mobile-exchange",
                "POST",
                "/api/v1/auth/mobile/exchange",
                Map.of("code", code),
                null,
                200));
    assertThat(
            body(call(
                    "account-mobile-authenticated-me",
                    "GET",
                    "/api/v1/users/me",
                    null,
                    exchanged.path("accessToken").asText(),
                    200))
                .path("id")
                .asLong())
        .isEqualTo(owner.getId());
    call(
        "account-mobile-exchange-replay-rejected",
        "POST",
        "/api/v1/auth/mobile/exchange",
        Map.of("code", code),
        null,
        401);
    String previous = exchanged.path("refreshToken").asText();
    var refreshed =
        body(
            call(
                "account-mobile-refresh",
                "POST",
                "/api/v1/auth/mobile/refresh",
                Map.of("refreshToken", previous),
                null,
                200));
    var parsedPrevious = jwt.parseRefreshToken(previous);
    var parsed = jwt.parseRefreshToken(refreshed.path("refreshToken").asText());
    assertThat(refreshTokens.exists(owner.getId(), parsedPrevious.jti())).isFalse();
    assertThat(refreshTokens.exists(owner.getId(), parsed.jti())).isTrue();
    call(
        "account-mobile-logout",
        "POST",
        "/api/v1/auth/mobile/logout",
        Map.of("refreshToken", refreshed.path("refreshToken").asText()),
        null,
        204);
    assertThat(refreshTokens.exists(owner.getId(), parsed.jti())).isFalse();
    call(
        "account-mobile-logged-out-refresh-rejected",
        "POST",
        "/api/v1/auth/mobile/refresh",
        Map.of("refreshToken", refreshed.path("refreshToken").asText()),
        null,
        401);
  }

  @Test
  void appleIdentityLinksToExistingAccountThroughWebAndMobileDelivery() throws Exception {
    Map<String, Object> identity =
        Map.of("identityToken", "provider-verified-fixture", "nonce", "fixture-nonce");
    var web = call("account-apple-web-login", "POST", "/api/v1/auth/apple", identity, null, 200);
    assertThat(
            body(call(
                    "account-apple-web-me",
                    "GET",
                    "/api/v1/users/me",
                    null,
                    body(web).path("accessToken").asText(),
                    200))
                .path("id")
                .asLong())
        .isEqualTo(owner.getId());
    assertThat(refreshCookie(web)).startsWith("refresh_token=");
    var mobile =
        body(
            call(
                "account-apple-mobile-login",
                "POST",
                "/api/v1/auth/mobile/apple",
                identity,
                null,
                200));
    var parsed = jwt.parseRefreshToken(mobile.path("refreshToken").asText());
    assertThat(parsed.userId()).isEqualTo(owner.getId());
    assertThat(refreshTokens.exists(owner.getId(), parsed.jti())).isTrue();
    assertThat(count("select count(*) from users where email=?", owner.getEmail())).isEqualTo(1);
  }

  @Test
  void enrollsTwoFactorCompletesWebAndMobileLoginAndConsumesRecoveryCode() throws Exception {
    assertThat(
            body(call("account-two-factor-status", "GET", "/api/v1/2fa/status", null, token, 200))
                .path("enabled")
                .asBoolean())
        .isFalse();
    String secret =
        body(call("account-two-factor-setup", "POST", "/api/v1/2fa/setup", null, token, 200))
            .path("secret")
            .asText();
    assertThat(
            jdbc.queryForObject(
                "select secret from user_two_factor where user_id=?", String.class, owner.getId()))
        .startsWith("v1:")
        .doesNotContain(secret);
    var confirmation =
        body(
            call(
                "account-two-factor-confirm",
                "POST",
                "/api/v1/2fa/confirm",
                Map.of("code", totp(secret)),
                token,
                200));
    assertThat(confirmation.path("recoveryCodes").size()).isEqualTo(10);
    assertThat(
            count(
                "select count(*) from user_two_factor where user_id=? and enabled=true",
                owner.getId()))
        .isEqualTo(1);
    Map<String, Object> identity =
        Map.of("identityToken", "provider-verified-fixture", "nonce", "fixture-nonce");
    var webChallenge =
        body(
            call(
                "account-two-factor-web-challenge",
                "POST",
                "/api/v1/auth/apple",
                identity,
                null,
                200));
    assertThat(webChallenge.has("accessToken")).isFalse();
    String loginCode = totp(secret);
    call(
        "account-two-factor-web-complete",
        "POST",
        "/api/v1/auth/2fa/verify",
        Map.of(
            "challenge",
            webChallenge.path("challenge").asText(),
            "code",
            loginCode,
            "recovery",
            false),
        null,
        200);
    call(
        "account-two-factor-totp-replay-rejected",
        "POST",
        "/api/v1/auth/2fa/verify",
        Map.of(
            "challenge",
            webChallenge.path("challenge").asText(),
            "code",
            loginCode,
            "recovery",
            false),
        null,
        401);
    var mobileChallenge =
        body(
            call(
                "account-two-factor-mobile-challenge",
                "POST",
                "/api/v1/auth/mobile/apple",
                identity,
                null,
                200));
    String recovery = confirmation.path("recoveryCodes").get(0).asText();
    call(
        "account-two-factor-mobile-complete",
        "POST",
        "/api/v1/auth/mobile/2fa/verify",
        Map.of(
            "challenge",
            mobileChallenge.path("challenge").asText(),
            "code",
            recovery,
            "recovery",
            true),
        null,
        200);
    assertThat(
            jdbc.queryForObject(
                    "select recovery_codes from user_two_factor where user_id=?",
                    String.class,
                    owner.getId())
                .lines()
                .count())
        .isEqualTo(9);
    call(
        "account-two-factor-recovery-replay-rejected",
        "POST",
        "/api/v1/auth/mobile/2fa/verify",
        Map.of(
            "challenge",
            mobileChallenge.path("challenge").asText(),
            "code",
            recovery,
            "recovery",
            true),
        null,
        401);
    // 로그인에 쓴 TOTP는 재사용할 수 없어, 허용 오차 안의 다음 코드를 사용한다.
    long nextStep =
        Math.max(
            Instant.now().getEpochSecond() / TotpCodec.PERIOD_SECONDS,
            count("select last_verified_step from user_two_factor where user_id=?", owner.getId())
                + 1);
    var regenerated =
        body(
            call(
                "account-two-factor-regenerate",
                "POST",
                "/api/v1/2fa/recovery-codes/regenerate",
                Map.of("code", TotpCodec.generateCode(secret, nextStep)),
                token,
                200));
    assertThat(regenerated.path("recoveryCodes").size()).isEqualTo(10);
    call(
        "account-two-factor-disable",
        "POST",
        "/api/v1/2fa/disable",
        Map.of("code", regenerated.path("recoveryCodes").get(0).asText()),
        token,
        200);
    assertThat(
            count(
                "select count(*) from user_two_factor where user_id=? and enabled=false and recovery_codes is null",
                owner.getId()))
        .isEqualTo(1);
  }

  @Test
  void commitsAndClearsAvatarAndBannerWithRealUserPersistence() throws Exception {
    for (String kind : new String[] {"avatar", "banner"}) {
      String path = "/api/v1/users/me/" + kind;
      String key =
          body(call(
                  "account-" + kind + "-presign",
                  "POST",
                  path + "/presigned-url",
                  Map.of("contentType", "image/png"),
                  token,
                  200))
              .path("key")
              .asText();
      assertThat(key).startsWith(kind + "s/" + owner.getId() + "/");
      call(
          "account-" + kind + "-cross-user-commit-rejected",
          "PUT",
          path,
          Map.of("key", key),
          strangerToken,
          400);
      call("account-" + kind + "-commit", "PUT", path, Map.of("key", key), token, 200);
      assertThat(
              jdbc.queryForObject(
                  "select " + kind + "_key from users where id=?", String.class, owner.getId()))
          .isEqualTo(key);
      call("account-" + kind + "-clear", "DELETE", path, null, token, 200);
      assertThat(
              count(
                  "select count(*) from users where id=? and " + kind + "_key is null",
                  owner.getId()))
          .isEqualTo(1);
    }
  }

  private ExportFixture commitOwnedAndForeignExportData() {
    return transactions.execute(
        status -> {
          LinkEntity ownedLink =
              links.save(
                  new LinkEntity(
                      "https://example.com/owned-export",
                      "eo" + owner.getId(),
                      owner.getId(),
                      null));
          LinkEntity foreignLink =
              links.save(
                  new LinkEntity(
                      "https://example.com/private-export",
                      "ef" + stranger.getId(),
                      stranger.getId(),
                      null));
          ClickEventEntity ownedClick =
              clicks.save(
                  ClickEventEntity.builder()
                      .linkId(ownedLink.linkId())
                      .clickedAt(Instant.parse("2026-01-15T00:00:00Z"))
                      .countryCode("JP")
                      .referrerHost("owned-referrer.example.com")
                      .deviceClass("desktop")
                      .bot(false)
                      .build());
          ClickEventEntity foreignClick =
              clicks.save(
                  ClickEventEntity.builder()
                      .linkId(foreignLink.linkId())
                      .clickedAt(Instant.parse("2026-01-15T00:01:00Z"))
                      .countryCode("US")
                      .referrerHost("private-referrer.example.com")
                      .deviceClass("mobile")
                      .bot(false)
                      .build());
          return new ExportFixture(
              ownedLink, foreignLink, ownedClick.getId(), foreignClick.getId());
        });
  }

  private record ExportFixture(
      LinkEntity ownedLink, LinkEntity foreignLink, Long ownedClickId, Long foreignClickId) {}

  private static String refreshCookie(HttpResponse<String> response) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(value -> value.startsWith("refresh_token="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private static String totp(String secret) {
    return TotpCodec.generateCode(
        secret, Instant.now().getEpochSecond() / TotpCodec.PERIOD_SECONDS);
  }
}
