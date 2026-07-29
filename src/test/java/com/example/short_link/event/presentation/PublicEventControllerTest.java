package com.example.short_link.event.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.pow.PowService;
import com.example.short_link.event.application.read.PublicEventQueryService;
import com.example.short_link.event.application.read.PublicEventView;
import com.example.short_link.event.application.write.CancelRegistrationUseCase;
import com.example.short_link.event.application.write.RegisterForEventCommand;
import com.example.short_link.event.application.write.RegisterForEventUseCase;
import com.example.short_link.event.application.write.RegistrationResult;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = PublicEventController.class)
@Import(EventExceptionHandler.class)
class PublicEventControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PublicEventQueryService publicEventQueryService;
  @MockitoBean private RegisterForEventUseCase registerForEvent;
  @MockitoBean private CancelRegistrationUseCase cancelRegistration;
  @MockitoBean private PowService powService;

  private PublicEventView view() {
    return new PublicEventView(
        "slug123abc",
        "스터디 모집",
        "설명",
        null,
        Instant.parse("2026-08-05T10:00:00Z"),
        null,
        "Asia/Tokyo",
        "시부야",
        null,
        null,
        10,
        3,
        null,
        "EMAIL",
        "OPEN",
        true,
        List.of());
  }

  @Test
  void publicRead_worksWithoutAuth() throws Exception {
    when(publicEventQueryService.findBySlug("slug123abc")).thenReturn(view());
    mvc.perform(get("/api/v1/public/events/slug123abc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("스터디 모집"))
        .andExpect(jsonPath("$.spotsLeft").value(3));
  }

  @Test
  void register_withoutPow_whenNotEnforced() throws Exception {
    when(powService.isEnforced()).thenReturn(false);
    when(registerForEvent.execute(any(RegisterForEventCommand.class)))
        .thenReturn(new RegistrationResult(7L, "token123", 2));

    mvc.perform(
            post("/api/v1/public/events/slug123abc/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"김철수\",\"contact\":\"a@b.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cancelToken").value("token123"));
  }

  @Test
  void register_powEnforced_rejectsMissingProof() throws Exception {
    when(powService.isEnforced()).thenReturn(true);
    when(powService.verifyAndConsume(null, null)).thenReturn(false);

    mvc.perform(
            post("/api/v1/public/events/slug123abc/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"김철수\",\"contact\":\"a@b.com\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void register_blankName_isBadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/public/events/slug123abc/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"contact\":\"a@b.com\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cancel_returns204() throws Exception {
    mvc.perform(
            post("/api/v1/public/events/registrations/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\"}"))
        .andExpect(status().isNoContent());
  }
}
