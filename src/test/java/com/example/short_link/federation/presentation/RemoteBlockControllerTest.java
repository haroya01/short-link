package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteBlocks;
import com.example.short_link.federation.application.RemoteMuteView;
import com.example.short_link.federation.application.RemoteMutes;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {RemoteBlockController.class, FederationExceptionHandler.class})
class RemoteBlockControllerTest {

  private static RemoteAccountView alice(boolean blocking, boolean muting) {
    return new RemoteAccountView(
        42L,
        "alice@mastodon.example",
        "alice",
        "mastodon.example",
        "Alice",
        null,
        "https://mastodon.example/@alice",
        false,
        false,
        false,
        blocking,
        muting);
  }

  @Autowired private MockMvc mvc;
  @MockitoBean private RemoteBlocks blocks;
  @MockitoBean private RemoteMutes mutes;

  @Test
  void blocksAndUnblocksAnAccountOnAnotherServer() throws Exception {
    when(blocks.block(7L, 42L)).thenReturn(alice(true, false));
    when(blocks.unblock(7L, 42L)).thenReturn(alice(false, false));
    when(blocks.blocks(7L, 0, 50)).thenReturn(List.of(alice(true, false)));

    mvc.perform(
            put("/api/v1/federation/accounts/42/block")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocking").value(true))
        .andExpect(jsonPath("$.muting").value(false))
        .andExpect(jsonPath("$.domainBlocked").value(false));
    mvc.perform(
            delete("/api/v1/federation/accounts/42/block")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocking").value(false));
    mvc.perform(
            get("/api/v1/federation/blocks")
                .param("page", "-1")
                .param("size", "500")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.items[0].blocking").value(true));
    mvc.perform(put("/api/v1/federation/accounts/42/block")).andExpect(status().isUnauthorized());
  }

  @Test
  void mutesWithOrWithoutABodyAndListsWhenEachMuteEnds() throws Exception {
    Instant ends = Instant.parse("2026-10-16T00:00:00Z");
    when(mutes.mute(7L, 42L, false, 3600L)).thenReturn(alice(false, true));
    when(mutes.mute(7L, 43L, null, null)).thenReturn(alice(false, true));
    when(mutes.unmute(7L, 42L)).thenReturn(alice(false, false));
    when(mutes.mutes(7L, 1, 1))
        .thenReturn(List.of(new RemoteMuteView(alice(false, true), ends, false)));

    mvc.perform(
            put("/api/v1/federation/accounts/42/mute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notifications\":false,\"duration\":3600}")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.muting").value(true));
    mvc.perform(
            put("/api/v1/federation/accounts/43/mute")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.muting").value(true));
    mvc.perform(
            delete("/api/v1/federation/accounts/42/mute")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.muting").value(false));
    mvc.perform(
            get("/api/v1/federation/mutes")
                .param("page", "1")
                .param("size", "0")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].account.acct").value("alice@mastodon.example"))
        .andExpect(jsonPath("$.items[0].expiresAt").value("2026-10-16T00:00:00Z"))
        .andExpect(jsonPath("$.items[0].notifications").value(false));
  }

  @Test
  void aMuteOutsideAMinuteToAYearIsABadRequest() throws Exception {
    when(mutes.mute(7L, 42L, true, 5L))
        .thenThrow(new FederationException(FederationErrorCode.MUTE_DURATION_INVALID));

    mvc.perform(
            put("/api/v1/federation/accounts/42/mute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notifications\":true,\"duration\":5}")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MUTE_DURATION_INVALID"));
  }
}
