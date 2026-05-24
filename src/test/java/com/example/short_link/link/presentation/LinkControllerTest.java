package com.example.short_link.link.presentation;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
class LinkControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @Test
  void createsShortLink() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/path\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("http://localhost:8080/")))
        .andExpect(jsonPath("$.shortCode").isString())
        .andExpect(jsonPath("$.shortUrl").isString());
  }

  @Test
  void rejectsBlankUrl() throws Exception {
    mvc.perform(
            post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsInvalidUrl() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"not-a-valid-url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.errors[0].field").value("url"));
  }

  @Test
  void rejectsTooLongUrl() throws Exception {
    String longUrl = "https://example.com/" + "a".repeat(3000);
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + longUrl + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsJavascriptScheme() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsDataScheme() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"data:text/html,<script>alert(1)</script>\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createsWithCustomCodeWhenAuthenticated() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-c1"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"myCustom\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortCode").value("myCustom"));
  }

  @Test
  void rejectsCustomCodeWithInvalidFormat() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-c2"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"with spaces\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void rejectsCustomCodeTooShort() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-c3"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"ab\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsDuplicateCustomCode() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-c4"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://first.com\",\"customCode\":\"taken12\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://second.com\",\"customCode\":\"taken12\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_SHORT_CODE"));
  }

  @Test
  void ignoresCustomCodeWhenAnonymous() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"requested\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortCode").value(not("requested")));
  }
}
