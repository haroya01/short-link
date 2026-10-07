package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {RemoteAccountController.class, FederationExceptionHandler.class})
class RemoteAccountControllerTest {

  private static final RemoteAccountView ALICE =
      new RemoteAccountView(
          42L,
          "alice@mastodon.example",
          "alice",
          "mastodon.example",
          "Alice",
          null,
          "https://mastodon.example/@alice",
          false,
          true,
          false);

  @Autowired private MockMvc mvc;
  @MockitoBean private RemoteFollowing following;

  @Test
  void looksUpFollowsAndUnfollowsAnAccountOnAnotherServer() throws Exception {
    when(following.lookup(7L, "@alice@mastodon.example")).thenReturn(ALICE);
    when(following.account(7L, 42L)).thenReturn(ALICE);
    when(following.follow(7L, 42L)).thenReturn(ALICE);
    when(following.unfollow(7L, 42L))
        .thenReturn(
            new RemoteAccountView(
                42L,
                "alice@mastodon.example",
                "alice",
                "mastodon.example",
                "Alice",
                null,
                "https://mastodon.example/@alice",
                false,
                false,
                false));

    mvc.perform(
            get("/api/v1/federation/accounts/lookup")
                .param("acct", "@alice@mastodon.example")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(42))
        .andExpect(jsonPath("$.acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.requested").value(true));
    mvc.perform(
            get("/api/v1/federation/accounts/42")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice"));
    mvc.perform(
            post("/api/v1/federation/accounts/42/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(true));
    mvc.perform(
            delete("/api/v1/federation/accounts/42/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requested").value(false));
    mvc.perform(get("/api/v1/federation/accounts/lookup").param("acct", "a@b.example"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listsFollowsWithAClampedPage() throws Exception {
    when(following.following(7L, 0, 50)).thenReturn(List.of(ALICE));

    mvc.perform(
            get("/api/v1/federation/following")
                .param("page", "-3")
                .param("size", "500")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].acct").value("alice@mastodon.example"));
  }

  @Test
  void errorsAreProblemDetailsWithTheirCode() throws Exception {
    when(following.lookup(7L, "nobody"))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID));
    when(following.follow(7L, 42L))
        .thenThrow(new FederationException(FederationErrorCode.FEDERATION_DISABLED));

    mvc.perform(
            get("/api/v1/federation/accounts/lookup")
                .param("acct", "nobody")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REMOTE_ACCOUNT_INVALID"));
    mvc.perform(
            post("/api/v1/federation/accounts/42/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("FEDERATION_DISABLED"));
  }
}
