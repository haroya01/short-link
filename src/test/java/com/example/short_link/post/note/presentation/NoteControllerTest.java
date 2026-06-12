package com.example.short_link.post.note.presentation;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.application.read.NoteFeedView;
import com.example.short_link.post.note.application.read.NoteQueryService;
import com.example.short_link.post.note.application.write.NoteCommandService;
import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.post.presentation.PostExceptionHandler;
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

/** HTTP 매핑·status·인증 게이트만 — 검증/소유권/멱등 규칙은 NoteServiceTest(DB)가 진짜로 돈다. */
@KurlWebMvcTest(controllers = NoteController.class)
@Import(PostExceptionHandler.class)
class NoteControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private NoteQueryService query;
  @MockitoBean private NoteCommandService command;

  private static final long USER_ID = 7L;

  private static NoteRow row(long id, String body) {
    return new NoteRow(
        id, body, Instant.parse("2026-06-12T00:00:00Z"), 0L, USER_ID, "tester", null);
  }

  @Test
  void feedIsPublic() throws Exception {
    when(query.feed(0, 20)).thenReturn(new NoteFeedView(List.of(row(1L, "첫 노트")), 0, false));

    mvc.perform(get("/api/v1/public/notes"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].body").value("첫 노트"))
        .andExpect(jsonPath("$.items[0].author.username").value("tester"))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void feedPassesPagingParams() throws Exception {
    when(query.feed(anyInt(), anyInt())).thenReturn(new NoteFeedView(List.of(), 2, false));

    mvc.perform(get("/api/v1/public/notes").param("page", "2").param("size", "10"))
        .andExpect(status().isOk());

    verify(query).feed(2, 10);
  }

  @Test
  void createReturns201WithRow() throws Exception {
    when(command.create(USER_ID, "방금 생각")).thenReturn(row(3L, "방금 생각"));

    mvc.perform(
            post("/api/v1/notes")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"방금 생각\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.body").value("방금 생각"));
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"익명\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void domainErrorMapsToEnvelope() throws Exception {
    when(command.create(USER_ID, "긴 글"))
        .thenThrow(new PostException(PostErrorCode.NOTE_BODY_TOO_LONG));

    mvc.perform(
            post("/api/v1/notes")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"긴 글\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NOTE_BODY_TOO_LONG"));
  }

  @Test
  void deleteReturns204() throws Exception {
    mvc.perform(delete("/api/v1/notes/9").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isNoContent());

    verify(command).delete(USER_ID, 9L);
  }

  @Test
  void likeToggleRoundTrips() throws Exception {
    when(command.setLike(USER_ID, 5L, true))
        .thenReturn(new NoteCommandService.LikeStatus(true, 1L));
    when(command.setLike(USER_ID, 5L, false))
        .thenReturn(new NoteCommandService.LikeStatus(false, 0L));

    mvc.perform(
            put("/api/v1/notes/5/like").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(true))
        .andExpect(jsonPath("$.likeCount").value(1));

    mvc.perform(
            delete("/api/v1/notes/5/like").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liked").value(false));
  }

  @Test
  void likeStatusReturnsBatchIds() throws Exception {
    when(query.likedNoteIds(USER_ID, List.of(1L, 2L, 3L))).thenReturn(List.of(2L));

    mvc.perform(
            get("/api/v1/notes/like-status")
                .param("ids", "1,2,3")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likedIds[0]").value(2));
  }
}
