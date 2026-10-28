package com.example.short_link.user.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.read.UserQueryService;
import com.example.short_link.user.application.write.ApiKeyService;
import com.example.short_link.user.domain.ApiKeyEntity;
import com.example.short_link.user.domain.UserEntity;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class ApiKeyAuthenticationFilterTest {

  private static final String RAW_KEY = "kurl_abcdefghijklmnop";

  private final ApiKeyService apiKeys = mock(ApiKeyService.class);
  private final UserQueryService users = mock(UserQueryService.class);
  private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeys, users);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
    MDC.clear();
  }

  private static UserEntity user(long id) {
    UserEntity user = new UserEntity("u@x.com", "google", "g-" + id);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private static ApiKeyEntity key(long id, long userId) {
    ApiKeyEntity key = new ApiKeyEntity(userId, "kurl_abcd", "hash", "cli");
    ReflectionTestUtils.setField(key, "id", id);
    return key;
  }

  private MockFilterChain run(MockHttpServletRequest request) throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return chain;
  }

  private static MockHttpServletRequest withHeader(String name, String value) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
    request.addHeader(name, value);
    return request;
  }

  @Test
  void aKurlKeyInTheBearerHeaderSignsInItsActiveOwner() throws Exception {
    when(apiKeys.resolve(RAW_KEY)).thenReturn(Optional.of(key(7L, 42L)));
    when(users.findActive(42L)).thenReturn(Optional.of(user(42L)));

    MockFilterChain chain = run(withHeader("Authorization", "Bearer " + RAW_KEY));

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getPrincipal()).isEqualTo(42L);
    assertThat(auth.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
    assertThat(MDC.get("userId")).isEqualTo("42");
    assertThat(MDC.get("authMethod")).isEqualTo("api-key");
    verify(apiKeys).recordUsage(7L);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void theXApiKeyHeaderSignsInTheSameWay() throws Exception {
    when(apiKeys.resolve(RAW_KEY)).thenReturn(Optional.of(key(7L, 42L)));
    when(users.findActive(42L)).thenReturn(Optional.of(user(42L)));

    run(withHeader("X-API-Key", RAW_KEY));

    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(42L);
  }

  @Test
  void aRequestThatIsAlreadySignedInKeepsItsAuthentication() throws Exception {
    TestingAuthenticationToken existing = new TestingAuthenticationToken(1L, null, "ROLE_USER");
    SecurityContextHolder.getContext().setAuthentication(existing);

    MockFilterChain chain = run(withHeader("X-API-Key", RAW_KEY));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    verify(apiKeys, never()).resolve(any());
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void aBearerTokenThatIsNotAKurlKeyIsLeftToTheOtherFilters() throws Exception {
    MockFilterChain chain = run(withHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(apiKeys, never()).resolve(any());
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void anUnknownOrRevokedKeyLeavesTheRequestAnonymous() throws Exception {
    when(apiKeys.resolve(RAW_KEY)).thenReturn(Optional.empty());

    MockFilterChain chain = run(withHeader("X-API-Key", RAW_KEY));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(apiKeys, never()).recordUsage(anyLong());
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void aKeyWhoseOwnerIsGoneOrBannedLeavesTheRequestAnonymous() throws Exception {
    when(apiKeys.resolve(RAW_KEY)).thenReturn(Optional.of(key(7L, 42L)));
    when(users.findActive(42L)).thenReturn(Optional.empty());

    MockFilterChain gone = run(withHeader("X-API-Key", RAW_KEY));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(gone.getRequest()).isNotNull();

    UserEntity banned = user(42L);
    banned.ban();
    when(users.findActive(42L)).thenReturn(Optional.of(banned));

    MockFilterChain bannedChain = run(withHeader("X-API-Key", RAW_KEY));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(MDC.get("authMethod")).isNull();
    verify(apiKeys, never()).recordUsage(anyLong());
    assertThat(bannedChain.getRequest()).isNotNull();
  }
}
