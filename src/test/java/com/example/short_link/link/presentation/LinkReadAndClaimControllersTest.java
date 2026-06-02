package com.example.short_link.link.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.pow.PowService;
import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.application.read.LinkDetailQueryService;
import com.example.short_link.link.application.write.ClaimAnonymousLinksCommand;
import com.example.short_link.link.application.write.ClaimAnonymousLinksUseCase;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Pow challenge / link detail / anonymous claim 컨트롤러 슬라이스. */
@KurlWebMvcTest(
    controllers = {PowController.class, LinkDetailController.class, AnonymousClaimController.class})
class LinkReadAndClaimControllersTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PowService powService;
  @MockitoBean private LinkDetailQueryService linkDetailQueryService;
  @MockitoBean private ClaimAnonymousLinksUseCase claimAnonymousLinksUseCase;

  private static final long USER_ID = 7L;

  // --- PowController ---

  @Test
  void challengeReturnsIssuedChallengeAndEnforcement() throws Exception {
    when(powService.issue()).thenReturn(new PowService.Challenge("chal-abc", 4));
    when(powService.isEnforced()).thenReturn(true);

    mvc.perform(
            get("/api/v1/pow/challenge").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challenge").value("chal-abc"))
        .andExpect(jsonPath("$.difficulty").value(4))
        .andExpect(jsonPath("$.enforced").value(true));
  }

  // --- LinkDetailController ---

  @Test
  void detailMapsViewToResponse() throws Exception {
    LinkDetailView view =
        new LinkDetailView(
            new ShortCode("abc123"),
            "https://example.com/origin",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            5,
            true,
            List.of("a", "b"),
            null,
            null);
    when(linkDetailQueryService.detail(eq(USER_ID), any(ShortCode.class))).thenReturn(view);

    mvc.perform(
            get("/api/v1/links/abc123/detail")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").value("abc123"))
        .andExpect(jsonPath("$.originalUrl").value("https://example.com/origin"))
        .andExpect(jsonPath("$.viewCount").value(5))
        .andExpect(jsonPath("$.tags.length()").value(2));
  }

  @Test
  void detailRejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/links/abc123/detail")).andExpect(status().isUnauthorized());
  }

  // --- AnonymousClaimController ---

  @Test
  void claimReturnsClaimedAndSkippedCounts() throws Exception {
    when(claimAnonymousLinksUseCase.execute(any(ClaimAnonymousLinksCommand.class)))
        .thenReturn(new ClaimResult(2, 1));

    mvc.perform(
            post("/api/v1/users/me/claim-anonymous")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimTokens\":[\"t1\",\"t2\",\"t3\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.claimed").value(2))
        .andExpect(jsonPath("$.skipped").value(1));
  }

  @Test
  void claimRejectsEmptyTokenList() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/claim-anonymous")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimTokens\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void claimRejectsAnonymous() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/claim-anonymous")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claimTokens\":[\"t1\"]}"))
        .andExpect(status().isUnauthorized());
  }
}
