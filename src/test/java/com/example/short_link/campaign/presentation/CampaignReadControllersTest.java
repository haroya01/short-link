package com.example.short_link.campaign.presentation;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.campaign.application.CampaignRecommendationService;
import com.example.short_link.campaign.application.CampaignStatsService;
import com.example.short_link.campaign.application.dto.CampaignRecommendationView;
import com.example.short_link.campaign.application.dto.CampaignStatsCompareView;
import com.example.short_link.campaign.application.dto.CampaignStatsCompareView.CampaignWithStats;
import com.example.short_link.campaign.application.dto.CampaignStatsView;
import com.example.short_link.campaign.exception.CampaignErrorCode;
import com.example.short_link.campaign.exception.CampaignException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** stats / compare / recommendation read controllers — 단일 책임이라 한 슬라이스에 묶음. */
@KurlWebMvcTest(
    controllers = {
      CampaignStatsController.class,
      CampaignStatsCompareController.class,
      CampaignRecommendationController.class
    })
@Import(CampaignExceptionHandler.class)
class CampaignReadControllersTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CampaignStatsService statsService;
  @MockitoBean private CampaignRecommendationService recommendationService;

  private static final long USER_ID = 7L;

  private CampaignStatsView statsView(long totalClicks) {
    return new CampaignStatsView(
        totalClicks,
        0,
        null,
        List.of(new CampaignStatsView.BatchStats(1L, "b1", "Kim", "East", 100, null, totalClicks)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  void statsReturnsView() throws Exception {
    when(statsService.statsFor(anyLong(), eq(USER_ID))).thenReturn(statsView(0));

    mvc.perform(
            get("/api/v1/campaigns/42/stats")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(0))
        .andExpect(jsonPath("$.byBatch.length()").value(1));
  }

  @Test
  void statsRejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/campaigns/42/stats")).andExpect(status().isUnauthorized());
  }

  @Test
  void statsOtherOwnerGets404() throws Exception {
    when(statsService.statsFor(anyLong(), eq(USER_ID)))
        .thenThrow(new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

    mvc.perform(
            get("/api/v1/campaigns/42/stats")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
  }

  @Test
  void compareReturnsBothCampaigns() throws Exception {
    when(statsService.compare(eq(List.of(1L, 2L)), eq(USER_ID)))
        .thenReturn(
            new CampaignStatsCompareView(
                List.of(
                    new CampaignWithStats(1L, "camp-a", statsView(3)),
                    new CampaignWithStats(2L, "camp-b", statsView(7)))));

    mvc.perform(
            post("/api/v1/campaigns/stats/compare")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campaignIds\":[1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.campaigns.length()").value(2));
  }

  @Test
  void compareRejectsSingleCampaignId() throws Exception {
    mvc.perform(
            post("/api/v1/campaigns/stats/compare")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campaignIds\":[1]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recommendationReturnsView() throws Exception {
    when(recommendationService.recommend(anyLong(), eq(USER_ID)))
        .thenReturn(new CampaignRecommendationView(false, null, 100, 0, 0.0, List.of()));

    mvc.perform(
            get("/api/v1/campaigns/42/recommendations")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalQuantity").value(100))
        .andExpect(jsonPath("$.insufficient").value(false));
  }

  @Test
  void recommendationOtherOwnerGets404() throws Exception {
    when(recommendationService.recommend(anyLong(), eq(USER_ID)))
        .thenThrow(new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

    mvc.perform(
            get("/api/v1/campaigns/42/recommendations")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNotFound());
  }
}
