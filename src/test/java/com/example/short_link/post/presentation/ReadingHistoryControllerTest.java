package com.example.short_link.post.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.ReadingHistoryEntryView;
import com.example.short_link.post.application.read.ReadingHistoryQueryService;
import com.example.short_link.post.application.read.ReadingHistoryView;
import com.example.short_link.post.application.write.RecordPostReadUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = ReadingHistoryController.class)
class ReadingHistoryControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RecordPostReadUseCase recordPostRead;
  @MockitoBean private ReadingHistoryQueryService readingHistoryQueryService;

  private static final long USER_ID = 9L;

  @Test
  void recordReturnsNoContent() throws Exception {
    mvc.perform(
            post("/api/v1/posts/5/read").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(recordPostRead).record(USER_ID, 5L);
  }

  @Test
  void historyReturnsPage() throws Exception {
    ReadingHistoryEntryView entry =
        new ReadingHistoryEntryView(
            5L,
            "bob",
            null,
            "Title",
            "slug",
            "excerpt",
            null,
            Instant.parse("2026-01-01T00:00:00Z"));
    when(readingHistoryQueryService.list(USER_ID, 0, 20))
        .thenReturn(new ReadingHistoryView(List.of(entry), 0, 20, false));

    mvc.perform(
            get("/api/v1/users/me/reading-history")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].title").value("Title"))
        .andExpect(jsonPath("$.items[0].username").value("bob"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void historyClampsPageAndSize() throws Exception {
    when(readingHistoryQueryService.list(USER_ID, 0, 50))
        .thenReturn(new ReadingHistoryView(List.of(), 0, 50, false));

    mvc.perform(
            get("/api/v1/users/me/reading-history")
                .param("page", "-2")
                .param("size", "999")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());

    verify(readingHistoryQueryService).list(USER_ID, 0, 50);
  }

  @Test
  void clearReturnsNoContent() throws Exception {
    mvc.perform(
            delete("/api/v1/users/me/reading-history")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(recordPostRead).clear(USER_ID);
  }

  @Test
  void forgetReturnsNoContent() throws Exception {
    mvc.perform(
            delete("/api/v1/users/me/reading-history/5")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(recordPostRead).remove(USER_ID, 5L);
  }
}
