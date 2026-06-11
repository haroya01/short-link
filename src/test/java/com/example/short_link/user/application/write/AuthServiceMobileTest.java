package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.user.application.dto.IssuedTokens;
import com.example.short_link.user.application.write.AuthService.LoginResult;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceMobileTest {

  @Autowired private AuthService authService;
  @Autowired private UserRepository userRepository;

  @Test
  void mobileLoginYieldsRedeemableExchangeCode() {
    UserEntity user = userRepository.save(new UserEntity("app@x.com", "google", "g-app1"));

    LoginResult result = authService.loginWithOAuthMobile("app@x.com", "google", "g-app1");

    assertThat(result).isInstanceOf(LoginResult.MobileExchangeCode.class);
    String code = ((LoginResult.MobileExchangeCode) result).code();
    IssuedTokens tokens = authService.exchangeMobileCode(code);
    assertThat(tokens.accessToken()).isNotBlank();
    assertThat(tokens.refreshToken()).isNotBlank();
    assertThat(userRepository.findById(user.getId())).isPresent();
  }

  @Test
  void mobileLoginUpsertsFirstTimeUser() {
    LoginResult result = authService.loginWithOAuthMobile("new@x.com", "google", "g-app2");

    assertThat(result).isInstanceOf(LoginResult.MobileExchangeCode.class);
    assertThat(userRepository.findByOauthProviderAndOauthId("google", "g-app2")).isPresent();
  }

  @Test
  void exchangeRejectsUnknownCode() {
    assertThatThrownBy(() -> authService.exchangeMobileCode("never-issued"))
        .isInstanceOf(UserException.class);
  }
}
