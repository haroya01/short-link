package com.example.short_link.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.write.AuthService;
import com.example.short_link.user.application.write.AuthService.LoginResult;
import com.example.short_link.user.presentation.helper.RefreshCookieWriter;
import com.example.short_link.user.presentation.request.DevLoginRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DevAuthControllerMobileResultTest {

  @Mock private AuthService authService;
  @Mock private RefreshCookieWriter refreshCookieWriter;
  @Mock private HttpServletResponse res;

  @Test
  void devLoginNeverServesMobileExchangeCode() {
    when(authService.loginWithOAuth(any(), any(), any()))
        .thenReturn(new LoginResult.MobileExchangeCode("code"));
    DevAuthController controller = new DevAuthController(authService, refreshCookieWriter);

    ResponseEntity<?> out = controller.devLogin(new DevLoginRequest("d@x.com"), res);

    assertThat(out.getStatusCode().is5xxServerError()).isTrue();
  }
}
