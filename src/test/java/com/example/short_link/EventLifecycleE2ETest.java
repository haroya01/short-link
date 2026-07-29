package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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
class EventLifecycleE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  private String createEvent(String token, int capacity) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/events")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"도쿄 개발자 스터디","descriptionMd":"매주 수요일 모임",
                         "startsAt":"2026-08-05T10:00:00Z","timezone":"Asia/Tokyo",
                         "locationText":"시부야","capacity":%d,"contactField":"EMAIL",
                         "questions":[{"type":"SHORT_TEXT","label":"어떤 주제에 관심 있나요?","required":false}]}
                        """
                            .formatted(capacity)))
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

    // 공개 페이지 — 비로그인 읽기
    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("도쿄 개발자 스터디"))
        .andExpect(jsonPath("$.acceptingRegistrations").value(true))
        .andExpect(jsonPath("$.spotsLeft").value(1));

    // 익명 신청 (정원 1)
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

    // 만석 — 다음 신청 거절
    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자B\",\"contact\":\"guest-b@x.com\"}"))
        .andExpect(status().isConflict());

    // 같은 연락처 중복 신청 거절
    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자A\",\"contact\":\"guest-a@x.com\"}"))
        .andExpect(status().isConflict());

    // 토큰 취소 → 자리가 다시 생긴다
    mvc.perform(
            post("/api/v1/public/events/registrations/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + cancelToken + "\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/public/events/" + slug))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spotsLeft").value(1));

    // 취소했던 연락처 재신청 — CANCELED 행 재활성화
    mvc.perform(
            post("/api/v1/public/events/" + slug + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"참가자A\",\"contact\":\"guest-a@x.com\"}"))
        .andExpect(status().isCreated());

    // 주최자 명단 + CSV + 분석
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

    // 다른 유저는 주최자 API 접근 불가
    UserEntity stranger = userRepository.save(new UserEntity("ev-str@x.com", "google", "g-ev-str"));
    String strangerToken = jwt.createAccessToken(stranger.getId(), "USER");
    mvc.perform(
            get("/api/v1/events/" + eventId + "/attendees")
                .header("Authorization", "Bearer " + strangerToken))
        .andExpect(status().isForbidden());
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
