package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.federation.application.DomainBlocks;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {DomainBlockController.class, FederationExceptionHandler.class})
class DomainBlockControllerTest {

  private static final DomainBlocks.View SOCIAL =
      new DomainBlocks.View("mastodon.social", Instant.parse("2026-10-07T00:00:00Z"));

  @Autowired private MockMvc mvc;
  @MockitoBean private DomainBlocks domainBlocks;

  @Test
  void aMemberListsBlocksAndUnblocksAServerWhoseNameHasDots() throws Exception {
    when(domainBlocks.list(7L)).thenReturn(List.of(SOCIAL));
    when(domainBlocks.block(7L, "mastodon.social")).thenReturn(SOCIAL);

    mvc.perform(
            get("/api/v1/federation/domain-blocks")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].domain").value("mastodon.social"));
    mvc.perform(
            put("/api/v1/federation/domain-blocks/mastodon.social")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.domain").value("mastodon.social"));
    mvc.perform(
            delete("/api/v1/federation/domain-blocks/mastodon.social")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isNoContent());
    verify(domainBlocks).unblock(7L, "mastodon.social");
  }

  @Test
  void aNameThatIsNoServerIs400() throws Exception {
    when(domainBlocks.block(7L, "nope"))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_DOMAIN_INVALID));

    mvc.perform(
            put("/api/v1/federation/domain-blocks/nope")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("REMOTE_DOMAIN_INVALID"));
  }

  @Test
  void blockingNeedsASignedInMember() throws Exception {
    mvc.perform(put("/api/v1/federation/domain-blocks/mastodon.social"))
        .andExpect(status().isUnauthorized());
  }
}
