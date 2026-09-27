package com.example.short_link.event.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.event.application.image.EventCoverImageService;
import com.example.short_link.event.application.read.EventAnalyticsQueryService;
import com.example.short_link.event.application.read.EventQueryService;
import com.example.short_link.event.application.read.EventView;
import com.example.short_link.event.application.write.ChangeEventStatusUseCase;
import com.example.short_link.event.application.write.CreateEventAliasLinkUseCase;
import com.example.short_link.event.application.write.CreateEventCommand;
import com.example.short_link.event.application.write.CreateEventUseCase;
import com.example.short_link.event.application.write.UpdateEventUseCase;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = EventController.class)
@Import(EventExceptionHandler.class)
class EventControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private CreateEventUseCase createEvent;
  @MockitoBean private UpdateEventUseCase updateEvent;
  @MockitoBean private ChangeEventStatusUseCase changeStatus;
  @MockitoBean private CreateEventAliasLinkUseCase createAliasLink;
  @MockitoBean private EventQueryService eventQueryService;
  @MockitoBean private EventAnalyticsQueryService analyticsQueryService;
  @MockitoBean private EventCoverImageService coverImageService;

  private static final long USER_ID = 7L;

  private EventEntity entity() {
    EventEntity event =
        new EventEntity(
            USER_ID,
            "slug123abc",
            "스터디 모집",
            null,
            Instant.parse("2026-08-05T10:00:00Z"),
            null,
            "Asia/Tokyo",
            null,
            null,
            null,
            10,
            null,
            ContactField.EMAIL);
    ReflectionTestUtils.setField(event, "id", 42L);
    return event;
  }

  private EventView view() {
    return EventView.from(entity(), List.of(), List.of(), null);
  }

  @Test
  void anonymousCreateIs401() throws Exception {
    mvc.perform(
            post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"T\",\"startsAt\":\"2026-08-05T10:00:00Z\",\"timezone\":\"Asia/Tokyo\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createReturns201WithView() throws Exception {
    when(createEvent.execute(any(CreateEventCommand.class))).thenReturn(entity());
    when(eventQueryService.findOwnEvent(USER_ID, 42L)).thenReturn(view());

    mvc.perform(
            post("/api/v1/events")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"스터디 모집\",\"startsAt\":\"2026-08-05T10:00:00Z\","
                        + "\"timezone\":\"Asia/Tokyo\",\"capacity\":10}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.slug").value("slug123abc"))
        .andExpect(jsonPath("$.title").value("스터디 모집"));
  }

  @Test
  void createWithoutTitle_isBadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/events")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"2026-08-05T10:00:00Z\",\"timezone\":\"Asia/Tokyo\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void attendeesCsv_downloads() throws Exception {
    when(eventQueryService.exportAttendeesCsv(eq(USER_ID), eq(42L)))
        .thenReturn("name,contact,status,channel,registered_at\n");

    mvc.perform(
            get("/api/v1/events/42/attendees.csv")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(content().string(org.hamcrest.Matchers.startsWith("name,contact")));
  }
}
