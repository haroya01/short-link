package com.example.short_link.link.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.OgOverrideResult;
import com.example.short_link.link.application.OgOverrideService;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OgOverrideControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private OgOverrideService service;

  @Test
  void anonymousPatchIs401() throws Exception {
    mvc.perform(
            patch("/api/v1/links/abc1234/og")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ogTitle\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patchAcceptsValidOverride() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("o@x.com", "google", "g-og"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.update(eq(user.getId()), eq("abc1234"), eq("Hi"), eq(null), eq(null)))
        .thenReturn(new OgOverrideResult("abc1234", "Hi", null, null));

    mvc.perform(
            patch("/api/v1/links/abc1234/og")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ogTitle\":\"Hi\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ogTitle").value("Hi"));
  }

  @Test
  void patchRejectsOversizeTitle() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("oz@x.com", "google", "g-ogz"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    String tooLong = "a".repeat(301);

    mvc.perform(
            patch("/api/v1/links/abc1234/og")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ogTitle\":\"" + tooLong + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
