package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.PostReadStats;
import com.example.short_link.post.application.read.PostReadStatsService;
import com.example.short_link.post.application.read.SeriesDetailView;
import com.example.short_link.post.application.read.SeriesQueryService;
import com.example.short_link.post.application.read.SeriesView;
import com.example.short_link.post.application.write.CreateSeriesUseCase;
import com.example.short_link.post.application.write.DeleteSeriesUseCase;
import com.example.short_link.post.application.write.SetSeriesItemsCommand;
import com.example.short_link.post.application.write.SetSeriesItemsUseCase;
import com.example.short_link.post.application.write.SetSeriesPostsUseCase;
import com.example.short_link.post.application.write.UpdateSeriesUseCase;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = SeriesController.class)
@Import(PostExceptionHandler.class)
class SeriesControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateSeriesUseCase createSeries;
  @MockitoBean private UpdateSeriesUseCase updateSeries;
  @MockitoBean private DeleteSeriesUseCase deleteSeries;
  @MockitoBean private SetSeriesPostsUseCase setSeriesPosts;
  @MockitoBean private SetSeriesItemsUseCase setSeriesItems;
  @MockitoBean private SeriesQueryService seriesQueryService;
  @MockitoBean private PostReadStatsService readStats;

  private static final long USER_ID = 7L;

  private SeriesDetailView detail() {
    return new SeriesDetailView(
        new SeriesView(5L, "guide", "Guide", 0, 0, Instant.parse("2026-01-01T00:00:00Z"), null),
        List.of(),
        List.of());
  }

  @Test
  void seriesStatsReturnsReaderBreakdown() throws Exception {
    when(readStats.forSeries(any(), any())).thenReturn(PostReadStats.empty("Asia/Seoul"));

    mvc.perform(
            get("/api/v1/series/5/stats").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
        .andExpect(jsonPath("$.totalVisits").value(0));
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/series")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"guide\",\"title\":\"Guide\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsSeries() throws Exception {
    SeriesEntity created = new SeriesEntity(USER_ID, "guide", "Guide");
    when(createSeries.execute(any())).thenReturn(created);
    when(seriesQueryService.getMine(any(), any())).thenReturn(detail());

    mvc.perform(
            post("/api/v1/series")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"guide\",\"title\":\"Guide\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.series.slug").value("guide"));
  }

  @Test
  void listsMine() throws Exception {
    when(seriesQueryService.listMine(USER_ID))
        .thenReturn(
            List.of(
                new SeriesView(
                    5L, "guide", "Guide", 2, 3, Instant.parse("2026-01-01T00:00:00Z"), null)));

    mvc.perform(get("/api/v1/series").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].slug").value("guide"))
        .andExpect(jsonPath("$[0].postCount").value(2))
        .andExpect(jsonPath("$[0].itemCount").value(3));
  }

  @Test
  void setsPosts() throws Exception {
    when(seriesQueryService.getMine(USER_ID, 5L)).thenReturn(detail());

    mvc.perform(
            put("/api/v1/series/5/posts")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"postIds\":[3,1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.slug").value("guide"));
  }

  @Test
  void setsItems() throws Exception {
    when(seriesQueryService.getMine(USER_ID, 5L)).thenReturn(detail());

    mvc.perform(
            put("/api/v1/series/5/items")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"items\":[{\"type\":\"NOTE\",\"id\":40},{\"type\":\"POST\",\"id\":3}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.slug").value("guide"));

    verify(setSeriesItems)
        .execute(
            new SetSeriesItemsCommand(
                USER_ID,
                5L,
                List.of(
                    new SetSeriesItemsCommand.Item(SeriesItemType.NOTE, 40L),
                    new SetSeriesItemsCommand.Item(SeriesItemType.POST, 3L))));
  }

  @Test
  void anUnknownItemTypeIs400() throws Exception {
    mvc.perform(
            put("/api/v1/series/5/items")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"VIDEO\",\"id\":40}]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/v1/series/5/items")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"type\":\"NOTE\"}]}"))
        .andExpect(status().isBadRequest());
    verify(setSeriesItems, never()).execute(any());
  }

  @Test
  void deletesSeries() throws Exception {
    mvc.perform(delete("/api/v1/series/5").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());
  }
}
