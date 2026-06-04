package com.example.short_link.post.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PublicSeriesQueryService;
import com.example.short_link.post.application.read.SeriesSubscriptionQueryService;
import com.example.short_link.post.application.read.SeriesSubscriptionStatus;
import com.example.short_link.post.application.write.SubscribeSeriesUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = SeriesSubscriptionController.class)
class SeriesSubscriptionControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SubscribeSeriesUseCase subscribeSeriesUseCase;
  @MockitoBean private SeriesSubscriptionQueryService seriesSubscriptionQueryService;
  @MockitoBean private PublicSeriesQueryService publicSeriesQueryService;

  private static final long USER_ID = 7L;

  @Test
  void anonymousSubscribeIs401() throws Exception {
    mvc.perform(put("/api/v1/series/5/subscription")).andExpect(status().isUnauthorized());
  }

  @Test
  void subscribeReturnsStatus() throws Exception {
    when(subscribeSeriesUseCase.subscribe(USER_ID, 5L))
        .thenReturn(new SeriesSubscriptionStatus(true, 3L));

    mvc.perform(
            put("/api/v1/series/5/subscription")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscribed").value(true))
        .andExpect(jsonPath("$.subscriberCount").value(3));
  }

  @Test
  void unsubscribeReturnsStatus() throws Exception {
    when(subscribeSeriesUseCase.unsubscribe(USER_ID, 5L))
        .thenReturn(new SeriesSubscriptionStatus(false, 2L));

    mvc.perform(
            delete("/api/v1/series/5/subscription")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscribed").value(false));
  }

  @Test
  void statusReadsQueryService() throws Exception {
    when(seriesSubscriptionQueryService.status(USER_ID, 5L))
        .thenReturn(new SeriesSubscriptionStatus(true, 9L));

    mvc.perform(
            get("/api/v1/series/5/subscription")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subscriberCount").value(9));
  }

  @Test
  void subscribedSeriesReturnsCards() throws Exception {
    when(publicSeriesQueryService.subscribedSeries(USER_ID)).thenReturn(List.of());

    mvc.perform(
            get("/api/v1/users/me/subscribed-series")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());
  }

  @Test
  void subscribedSeriesIs401WhenAnonymous() throws Exception {
    mvc.perform(get("/api/v1/users/me/subscribed-series")).andExpect(status().isUnauthorized());
  }

  @Test
  void mySubscriptionsReturnsIds() throws Exception {
    when(seriesSubscriptionQueryService.mySubscriptions(USER_ID)).thenReturn(List.of(1L, 2L));

    mvc.perform(
            get("/api/v1/users/me/series-subscriptions")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(1))
        .andExpect(jsonPath("$[1]").value(2));
  }
}
