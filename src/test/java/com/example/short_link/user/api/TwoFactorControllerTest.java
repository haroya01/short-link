package com.example.short_link.user.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.twofactor.TwoFactorService;
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
class TwoFactorControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private TwoFactorService service;

  @Test
  void anonymousStatusIs401() throws Exception {
    mvc.perform(get("/api/v1/2fa/status")).andExpect(status().isUnauthorized());
  }

  @Test
  void statusReturnsServiceResult() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-2fa"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.status(eq(user.getId())))
        .thenReturn(new TwoFactorService.Status(true, Instant.parse("2026-01-01T00:00:00Z")));

    mvc.perform(get("/api/v1/2fa/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void setupReturnsSecretAndProvisioningUri() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("s@x.com", "google", "g-2sf"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.start(eq(user.getId())))
        .thenReturn(
            new TwoFactorService.SetupChallenge("JBSWY3DPEHPK3PXP", "otpauth://totp/x?secret=Y"));

    mvc.perform(post("/api/v1/2fa/setup").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secret").value("JBSWY3DPEHPK3PXP"))
        .andExpect(jsonPath("$.provisioningUri").value("otpauth://totp/x?secret=Y"));
  }

  @Test
  void confirmReturnsRecoveryCodes() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-2cf"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.confirm(eq(user.getId()), eq("123456")))
        .thenReturn(List.of("AAAA-AAAA", "BBBB-BBBB"));

    mvc.perform(
            post("/api/v1/2fa/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAA-AAAA"));
  }

  @Test
  void disableInvokesService() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-2dd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(service).disable(eq(user.getId()), eq("999999"));

    mvc.perform(
            post("/api/v1/2fa/disable")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"999999\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void regenerateReturnsNewCodes() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@x.com", "google", "g-2re"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.regenerateRecoveryCodes(eq(user.getId()), eq("777777")))
        .thenReturn(List.of("ZZZZ-ZZZZ"));

    mvc.perform(
            post("/api/v1/2fa/recovery-codes/regenerate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"777777\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recoveryCodes[0]").value("ZZZZ-ZZZZ"));
  }
}
