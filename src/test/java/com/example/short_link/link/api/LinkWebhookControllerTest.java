package com.example.short_link.link.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.application.LinkWebhookService;
import com.example.short_link.link.application.LinkWebhookService.IssuedWebhook;
import com.example.short_link.link.application.LinkWebhookService.WebhookSummary;
import com.example.short_link.link.application.WebhookFormat;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.time.Instant;
import java.util.List;
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
class LinkWebhookControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private LinkWebhookService service;

  private static WebhookSummary summary() {
    return new WebhookSummary(
        1L,
        "https://example.com/wh",
        "name",
        true,
        Instant.EPOCH,
        null,
        null,
        null,
        false,
        100,
        false,
        null,
        0,
        null,
        null,
        null,
        WebhookFormat.GENERIC);
  }

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/links/abc1234/webhooks")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsWebhooks() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("w@x.com", "google", "g-wh"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.list(eq(user.getId()), eq("abc1234"))).thenReturn(List.of(summary()));

    mvc.perform(get("/api/v1/links/abc1234/webhooks").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].url").value("https://example.com/wh"));
  }

  @Test
  void registerReturns201WithSecret() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@x.com", "google", "g-whr"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.register(eq(user.getId()), eq("abc1234"), anyString(), anyString()))
        .thenReturn(
            new IssuedWebhook(
                1L,
                "https://example.com/wh",
                "secret-once",
                "name",
                Instant.EPOCH,
                WebhookFormat.GENERIC));

    mvc.perform(
            post("/api/v1/links/abc1234/webhooks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/wh\",\"name\":\"name\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.secret").value("secret-once"));
  }

  @Test
  void toggleReturnsUpdatedSummary() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-wht"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.toggle(eq(user.getId()), eq("abc1234"), eq(1L), anyBoolean()))
        .thenReturn(summary());

    mvc.perform(
            patch("/api/v1/links/abc1234/webhooks/1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk());
  }

  @Test
  void updateConfigReturnsSummary() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-whc"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.updateConfig(eq(user.getId()), eq("abc1234"), eq(1L), any()))
        .thenReturn(summary());

    mvc.perform(
            put("/api/v1/links/abc1234/webhooks/1/config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"includeBots\":true,\"sampleRate\":50}"))
        .andExpect(status().isOk());
  }

  @Test
  void deleteReturns204() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-whd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(service).delete(anyLong(), anyString(), anyLong());

    mvc.perform(
            delete("/api/v1/links/abc1234/webhooks/1").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }
}
