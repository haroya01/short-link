package com.example.short_link.user.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.link.presentation.LinkController;
import com.example.short_link.user.presentation.UserController;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

class SuspendedWriteGateTest {

  private final UserModerationGuard moderation = mock(UserModerationGuard.class);
  private final SuspendedWriteGate gate = new SuspendedWriteGate(moderation);

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void asksAboutTheSignedInWriterOfAGatedModuleOnly() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));
    HandlerMethod create = handler(LinkController.class);
    HandlerMethod outside = handler(UserController.class);

    assertThat(gate.preHandle(request("POST"), new MockHttpServletResponse(), create)).isTrue();
    verify(moderation).requireCanWrite(7L);

    gate.preHandle(request("GET"), new MockHttpServletResponse(), create);
    gate.preHandle(request("POST"), new MockHttpServletResponse(), outside);
    gate.preHandle(request("POST"), new MockHttpServletResponse(), new Object());
    SecurityContextHolder.clearContext();
    gate.preHandle(request("DELETE"), new MockHttpServletResponse(), create);
    verify(moderation).requireCanWrite(any());
  }

  @Test
  void onlyTheApplicationsOwnModulesAreGated() {
    assertThat(SuspendedWriteGate.inGatedModule(LinkController.class)).isTrue();
    assertThat(SuspendedWriteGate.inGatedModule(UserController.class)).isFalse();
    assertThat(SuspendedWriteGate.inGatedModule(String.class)).isFalse();
    verify(moderation, never()).requireCanWrite(any());
  }

  private static HandlerMethod handler(Class<?> type) {
    return new HandlerMethod(new Object(), type.getDeclaredMethods()[0]) {
      @Override
      public Class<?> getBeanType() {
        return type;
      }
    };
  }

  private static MockHttpServletRequest request(String method) {
    return new MockHttpServletRequest(method, "/x");
  }
}
