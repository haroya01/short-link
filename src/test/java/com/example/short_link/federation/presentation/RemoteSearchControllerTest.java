package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.RemoteAccountSearch;
import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteResolution;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {RemoteSearchController.class, FederationExceptionHandler.class})
class RemoteSearchControllerTest {

  private static final RemoteAccountView ALICE =
      new RemoteAccountView(
          42L,
          "alice@mastodon.example",
          "alice",
          "mastodon.example",
          "Alice",
          null,
          "https://mastodon.example/@alice",
          true,
          false,
          false,
          false,
          false);

  @Autowired private MockMvc mvc;
  @MockitoBean private RemoteAccountSearch search;
  @MockitoBean private RemoteResolution resolution;

  @Test
  void anyoneSearchesKnownAccountsWithTenByDefault() throws Exception {
    when(search.search(null, "ali", 10)).thenReturn(List.of(ALICE));
    when(search.search(7L, "ali", 20)).thenReturn(List.of());

    mvc.perform(get("/api/v1/federation/accounts/search").param("q", "ali"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(42))
        .andExpect(jsonPath("$.items[0].acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.items[0].following").value(true))
        .andExpect(jsonPath("$.items[0].domainBlocked").value(false));
    mvc.perform(
            get("/api/v1/federation/accounts/search")
                .param("q", "ali")
                .param("limit", "20")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void aMemberGetsFiveSuggestionsByDefaultAndAnyoneElseIsTurnedAway() throws Exception {
    when(search.suggestions(7L, 5)).thenReturn(List.of(ALICE));
    when(search.suggestions(7L, 20)).thenReturn(List.of());

    mvc.perform(
            get("/api/v1/federation/accounts/suggestions")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.items[0].blocking").value(false))
        .andExpect(jsonPath("$.items[0].muting").value(false));
    mvc.perform(
            get("/api/v1/federation/accounts/suggestions")
                .param("limit", "20")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/v1/federation/accounts/suggestions"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void resolvingNeedsASignedInMember() throws Exception {
    mvc.perform(get("/api/v1/federation/resolve").param("q", "@alice@mastodon.example"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(resolution);
  }

  @Test
  void anAccountOrANoteComesBackWithTheOtherNull() throws Exception {
    when(resolution.resolve(7L, "@alice@mastodon.example"))
        .thenReturn(new RemoteResolution.Resolved(ALICE, null));
    when(resolution.resolve(7L, "https://mastodon.example/@alice/1"))
        .thenReturn(new RemoteResolution.Resolved(null, Map.of("id", 9, "body", "hello")));

    mvc.perform(
            get("/api/v1/federation/resolve")
                .param("q", "@alice@mastodon.example")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account.acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.note").isEmpty());
    mvc.perform(
            get("/api/v1/federation/resolve")
                .param("q", "https://mastodon.example/@alice/1")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.account").isEmpty())
        .andExpect(jsonPath("$.note.id").value(9))
        .andExpect(jsonPath("$.note.body").value("hello"));
  }

  @Test
  void eachFailureCarriesItsOwnCode() throws Exception {
    when(resolution.resolve(7L, ""))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_ACCOUNT_INVALID));
    when(resolution.resolve(7L, "@ghost@mastodon.example"))
        .thenThrow(
            new FederationException(
                FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, "ghost@mastodon.example"));
    when(resolution.resolve(7L, "@alice@down.example"))
        .thenThrow(
            new FederationException(FederationErrorCode.REMOTE_SERVER_UNREACHABLE, "down.example"));

    mvc.perform(
            get("/api/v1/federation/resolve").header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REMOTE_ACCOUNT_INVALID"));
    mvc.perform(
            get("/api/v1/federation/resolve")
                .param("q", "@ghost@mastodon.example")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REMOTE_ACCOUNT_NOT_FOUND"));
    mvc.perform(
            get("/api/v1/federation/resolve")
                .param("q", "@alice@down.example")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.code").value("REMOTE_SERVER_UNREACHABLE"))
        .andExpect(jsonPath("$.detail").value("down.example did not answer"));
  }
}
