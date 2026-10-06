package com.example.short_link.note.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.application.write.NoteFeedSettingsService;
import com.example.short_link.note.application.write.NoteImages;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = {NoteController.class, NoteExceptionHandler.class})
class NoteControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private NoteQueryService query;
  @MockitoBean private NoteCommandService command;
  @MockitoBean private NoteImages images;
  @MockitoBean private NoteFeedSettingsService feedSettings;

  @Test
  void aNoteErrorKeepsItsStatusAndCode() throws Exception {
    when(query.thread(eq(9L), any()))
        .thenThrow(new NoteException(NoteErrorCode.NOTE_NOT_FOUND, 9L));

    mvc.perform(get("/api/v1/public/notes/9"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTE_NOT_FOUND"));
  }

  @Test
  void moreThanFourImagesIsRejectedBeforeTheService() throws Exception {
    mvc.perform(
            post("/api/v1/notes")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"body\":\"x\",\"images\":[{\"key\":\"a\"},{\"key\":\"b\"},{\"key\":\"c\"},"
                        + "{\"key\":\"d\"},{\"key\":\"e\"}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theOriginalBodyOnlyRequestStillCreatesANote() throws Exception {
    when(command.create(any(), eq(new NoteDraft("hi", List.of(), null, null)))).thenReturn(null);

    mvc.perform(
            post("/api/v1/notes")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}"))
        .andExpect(status().isCreated());
  }
}
