package com.example.short_link.note.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.application.write.NoteScheduleService;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {NoteScheduleController.class, NoteExceptionHandler.class})
class NoteScheduleControllerTest {

  private static final Instant AT = Instant.parse("2026-10-08T00:00:00Z");
  private static final NoteScheduleService.View VIEW =
      new NoteScheduleService.View(3L, AT, "아침에", null, null, 0, false, null, null, null, null);

  @Autowired private MockMvc mvc;
  @MockitoBean private NoteScheduleService schedules;

  @Test
  void aMemberSchedulesListsMovesAndCancelsANote() throws Exception {
    when(schedules.schedule(eq(7L), any(NoteDraft.class), eq(AT))).thenReturn(VIEW);
    when(schedules.list(7L)).thenReturn(List.of(VIEW));
    when(schedules.reschedule(7L, 3L, AT.plusSeconds(3600))).thenReturn(VIEW);

    mvc.perform(
            post("/api/v1/notes/scheduled")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":{\"body\":\"아침에\"},\"scheduledAt\":\"2026-10-08T00:00:00Z\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.scheduledAt").value("2026-10-08T00:00:00Z"));
    mvc.perform(get("/api/v1/notes/scheduled").header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].body").value("아침에"));
    mvc.perform(
            patch("/api/v1/notes/scheduled/3")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"2026-10-08T01:00:00Z\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/v1/notes/scheduled/3")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7"))
        .andExpect(status().isNoContent());
    verify(schedules).cancel(7L, 3L);
  }

  @Test
  void tooSoonIs422AndANoteIsRequired() throws Exception {
    when(schedules.schedule(eq(7L), any(NoteDraft.class), any()))
        .thenThrow(new NoteException(NoteErrorCode.NOTE_SCHEDULE_TOO_SOON));

    mvc.perform(
            post("/api/v1/notes/scheduled")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":{\"body\":\"곧\"},\"scheduledAt\":\"2026-10-07T00:00:00Z\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("NOTE_SCHEDULE_TOO_SOON"));
    mvc.perform(
            post("/api/v1/notes/scheduled")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scheduledAt\":\"2026-10-08T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
  }
}
