package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class EventLifecycleE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private EntityManager entityManager;

  private String createEvent(String token, int capacity) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/events")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"도쿄 개발자 스터디","descriptionMd":"매주 수요일 모임",
                         "startsAt":"%s","timezone":"Asia/Tokyo",
                         "locationText":"시부야","capacity":%d,"contactField":"EMAIL",
                         "questions":[{"type":"SHORT_TEXT","label":"어떤 주제에 관심 있나요?","required":false}]}
                        """
                            .formatted(Instant.now().plus(7, ChronoUnit.DAYS), capacity)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").isNotEmpty())
            .andExpect(jsonPath("$.links").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body;
  }

  @Test
  void fullLifecycle_create_publicRead_register_full_cancel_reregister() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org@x.com", "google", "g-ev-org"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");

    String created = createEvent(token, 1);
    String slug = JsonPath.read(created, "$.slug");
    int eventId = JsonPath.read(created, "$.id");

    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("도쿄 개발자 스터디"))
        .andExpect(jsonPath("$.acceptingRegistrations").value(true))
        .andExpect(jsonPath("$.spotsLeft").value(1));

    String registered =
        mvc.perform(
                post("/api/v1/public/events/" + slug + "/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"참가자A\",\"contact\":\"guest-a@x.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cancelToken").isNotEmpty())
            .andExpect(jsonPath("$.spotsLeft").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cancelToken = JsonPath.read(registered, "$.cancelToken");

    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자B\",\"contact\":\"guest-b@x.com\"}"))
        .andExpect(status().isConflict());

    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자A\",\"contact\":\"guest-a@x.com\"}"))
        .andExpect(status().isConflict());

    mvc.perform(
            post("/api/v1/public/events/registrations/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + cancelToken + "\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spotsLeft").value(1));

    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자A\",\"contact\":\"guest-a@x.com\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/events/" + eventId + "/attendees")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].contact").value("guest-a@x.com"))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

    String csv =
        mvc.perform(
                get("/api/v1/events/" + eventId + "/attendees.csv")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(csv).contains("guest-a@x.com").contains("어떤 주제에 관심 있나요?");

    mvc.perform(
            get("/api/v1/events/" + eventId + "/analytics")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRegistrations").value(1));

    UserEntity stranger = userRepository.save(new UserEntity("ev-str@x.com", "google", "g-ev-str"));
    String strangerToken = jwt.createAccessToken(stranger.getId(), "USER");
    mvc.perform(
            get("/api/v1/events/" + eventId + "/attendees")
                .header("Authorization", "Bearer " + strangerToken))
        .andExpect(status().isForbidden());
  }

  private String timedEventBody(Instant startsAt, Instant endsAt, Instant closeAt) {
    return """
        {"title":"모임","startsAt":"%s","endsAt":%s,"closeAt":%s,"timezone":"Asia/Tokyo"}
        """
        .formatted(startsAt, quoted(endsAt), quoted(closeAt));
  }

  private static String quoted(Instant instant) {
    return instant == null ? "null" : "\"" + instant + "\"";
  }

  @Test
  void createAndUpdate_rejectEndBeforeStartAndDeadlineAfterEnd() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org3@x.com", "google", "g-ev-org3"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    Instant starts = Instant.now().plus(10, ChronoUnit.DAYS);

    mvc.perform(
            post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(timedEventBody(starts, starts.minus(3, ChronoUnit.DAYS), null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EVENT_DETAILS"))
        .andExpect(jsonPath("$.field").value("endsAt"));
    mvc.perform(
            post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(timedEventBody(starts, null, starts.plusSeconds(60))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_EVENT_DETAILS"))
        .andExpect(jsonPath("$.field").value("closeAt"));

    String created =
        mvc.perform(
                post("/api/v1/events")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(timedEventBody(starts, starts.plusSeconds(3600), starts)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int eventId = JsonPath.read(created, "$.id");

    mvc.perform(
            patch("/api/v1/events/" + eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(timedEventBody(starts, starts.minusSeconds(1), null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("endsAt"));
    mvc.perform(
            patch("/api/v1/events/" + eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    timedEventBody(starts, starts.plusSeconds(3600), starts.plusSeconds(3601))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.field").value("closeAt"));
  }

  private String createTimedEvent(String token, Instant startsAt, Instant endsAt) throws Exception {
    return mvc.perform(
            post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(timedEventBody(startsAt, endsAt, null)))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void assertEndedAndRejectsRegistration(String slug) throws Exception {
    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.acceptingRegistrations").value(false))
        .andExpect(jsonPath("$.registrationClosedReason").value("ENDED"));
    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자\",\"contact\":\"late@x.com\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_CLOSED"));
  }

  @Test
  void openEvent_reportsNoClosureReason() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org7@x.com", "google", "g-ev-org7"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    String created = createEvent(token, 10);
    String slug = JsonPath.read(created, "$.slug");
    int eventId = JsonPath.read(created, "$.id");

    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptingRegistrations").value(true))
        .andExpect(jsonPath("$.registrationClosedReason").doesNotExist());
    mvc.perform(get("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationClosedReason").doesNotExist());
  }

  @Test
  void eventPastItsEnd_isShownEndedAndRejectsRegistration() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org4@x.com", "google", "g-ev-org4"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    Instant now = Instant.now();
    String created =
        createTimedEvent(token, now.minus(5, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS));
    int eventId = JsonPath.read(created, "$.id");

    assertEndedAndRejectsRegistration(JsonPath.read(created, "$.slug"));
    mvc.perform(get("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.registrationClosedReason").value("ENDED"));
    mvc.perform(get("/api/v1/events").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].registrationClosedReason").value("ENDED"));
  }

  @Test
  void eventWithoutEndPastItsStart_isShownEndedAndRejectsRegistration() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org5@x.com", "google", "g-ev-org5"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    String created = createTimedEvent(token, Instant.now().minus(1, ChronoUnit.HOURS), null);

    assertEndedAndRejectsRegistration(JsonPath.read(created, "$.slug"));
  }

  @Test
  void storedEndBeforeStart_stillReadsAndRejectsRegistration() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org6@x.com", "google", "g-ev-org6"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    Instant starts = Instant.now().minus(80, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    Instant storedEnd = starts.minus(3, ChronoUnit.DAYS);
    String created = createTimedEvent(token, starts, null);
    String slug = JsonPath.read(created, "$.slug");
    int eventId = JsonPath.read(created, "$.id");
    entityManager
        .createQuery("UPDATE EventEntity e SET e.endsAt = :endsAt WHERE e.id = :id")
        .setParameter("endsAt", storedEnd)
        .setParameter("id", (long) eventId)
        .executeUpdate();
    entityManager.clear();

    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startsAt").value(starts.toString()))
        .andExpect(jsonPath("$.endsAt").value(storedEnd.toString()));
    mvc.perform(get("/api/v1/events/" + eventId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.endsAt").value(storedEnd.toString()));
    assertEndedAndRejectsRegistration(slug);
  }

  private String createFromBody(String token, String body) throws Exception {
    return mvc.perform(
            post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void changeStatus(String token, String created, String action) throws Exception {
    mvc.perform(
            post("/api/v1/events/" + JsonPath.read(created, "$.id") + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\"}"))
        .andExpect(status().isOk());
  }

  private String getJson(String url, String token) throws Exception {
    var request = get(url);
    if (token != null) {
      request = request.header("Authorization", "Bearer " + token);
    }
    return mvc.perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void ownerListAndDetailReportTheSameClosureReasonAsThePublicView() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org8@x.com", "google", "g-ev-org8"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    Instant now = Instant.now();
    Instant past = now.minus(5, ChronoUnit.DAYS);
    Map<String, String> expected = new LinkedHashMap<>();

    expected.put(createEvent(token, 10), null);

    String full = createEvent(token, 1);
    mvc.perform(
            post("/api/v1/public/events/" + JsonPath.read(full, "$.slug") + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자\",\"contact\":\"full@x.com\"}"))
        .andExpect(status().isCreated());
    expected.put(full, "FULL");

    expected.put(
        createFromBody(
            token,
            timedEventBody(now.plus(10, ChronoUnit.DAYS), null, now.minus(1, ChronoUnit.HOURS))),
        "DEADLINE");

    String closed = createEvent(token, 1);
    changeStatus(token, closed, "close");
    expected.put(closed, "CLOSED");

    String endedAndClosed = createTimedEvent(token, past, past.plus(1, ChronoUnit.HOURS));
    changeStatus(token, endedAndClosed, "close");
    expected.put(endedAndClosed, "ENDED");

    String canceledAfterEnd = createTimedEvent(token, past, past.plus(1, ChronoUnit.HOURS));
    changeStatus(token, canceledAfterEnd, "cancel");
    expected.put(canceledAfterEnd, "CANCELED");

    entityManager.flush();
    entityManager.clear();
    String list = getJson("/api/v1/events", token);

    for (Map.Entry<String, String> entry : expected.entrySet()) {
      int id = JsonPath.read(entry.getKey(), "$.id");
      String slug = JsonPath.read(entry.getKey(), "$.slug");
      List<Map<String, Object>> listed = JsonPath.read(list, "$[?(@.id == " + id + ")]");
      Map<String, Object> publicView =
          JsonPath.read(getJson("/api/v1/public/events/" + slug, null), "$");
      Map<String, Object> detail = JsonPath.read(getJson("/api/v1/events/" + id, token), "$");

      assertThat(publicView.get("registrationClosedReason"))
          .as("public %s", slug)
          .isEqualTo(entry.getValue());
      assertThat(detail.get("registrationClosedReason"))
          .as("detail %s", slug)
          .isEqualTo(entry.getValue());
      assertThat(listed)
          .singleElement()
          .satisfies(
              item ->
                  assertThat(item.get("registrationClosedReason"))
                      .as("list %s", slug)
                      .isEqualTo(entry.getValue()));
    }
  }

  @Test
  void invalidContact_rejectedByFieldType() throws Exception {
    UserEntity organizer =
        userRepository.save(new UserEntity("ev-org2@x.com", "google", "g-ev-org2"));
    String token = jwt.createAccessToken(organizer.getId(), "USER");
    String slug = JsonPath.read(createEvent(token, 10), "$.slug");

    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자\",\"contact\":\"이메일아님\"}"))
        .andExpect(status().isBadRequest());
  }
}
