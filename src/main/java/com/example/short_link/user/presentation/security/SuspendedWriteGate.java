package com.example.short_link.user.presentation.security;

import com.example.short_link.common.user.OpenToSuspended;
import com.example.short_link.common.user.UserModerationGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Notes and posts ask UserModerationGuard in their use cases; these modules are asked here, once
// per
// writing request, so a new handler is refused by default rather than by remembering to ask.
@Component
@RequiredArgsConstructor
public class SuspendedWriteGate implements HandlerInterceptor, WebMvcConfigurer {

  static final Set<String> MODULES =
      Set.of("link", "event", "room", "cta", "campaign", "customdomain");
  private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
  private static final String ROOT = "com.example.short_link.";

  private final UserModerationGuard moderation;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this);
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
    if (handler instanceof HandlerMethod method
        && WRITES.contains(req.getMethod())
        && gated(method)) {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof Long userId) {
        moderation.requireCanWrite(userId);
      }
    }
    return true;
  }

  static boolean gated(HandlerMethod method) {
    return inGatedModule(method.getBeanType())
        && !method.hasMethodAnnotation(OpenToSuspended.class);
  }

  static boolean inGatedModule(Class<?> type) {
    String name = type.getName();
    if (!name.startsWith(ROOT)) {
      return false;
    }
    String rest = name.substring(ROOT.length());
    int dot = rest.indexOf('.');
    return dot > 0 && MODULES.contains(rest.substring(0, dot));
  }
}
