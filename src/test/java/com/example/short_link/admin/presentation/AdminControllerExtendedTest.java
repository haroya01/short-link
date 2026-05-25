package com.example.short_link.admin.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerExtendedTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  private String adminToken() {
    UserEntity admin = userRepository.save(new UserEntity("a@x.com", "google", "g-adminx"));
    admin.promoteToAdmin();
    userRepository.save(admin);
    return jwt.createAccessToken(admin.getId(), "ADMIN");
  }

  @Test
  void redetectWebhookFormatsRequiresAdmin() throws Exception {
    mvc.perform(post("/api/v1/admin/webhooks/redetect-formats"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanRedetectWebhookFormats() throws Exception {
    mvc.perform(
            post("/api/v1/admin/webhooks/redetect-formats")
                .header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scanned").isNumber())
        .andExpect(jsonPath("$.formatChanged").isNumber());
  }

  @Test
  void requestRouteMetricsWithWindow() throws Exception {
    mvc.perform(
            get("/api/v1/admin/metrics/routes")
                .param("window", "1h")
                .header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk());
  }

  @Test
  void requestOutcomeDistribution() throws Exception {
    mvc.perform(
            get("/api/v1/admin/metrics/outcomes")
                .param("shortCode", "abc1234")
                .param("window", "24h")
                .header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk());
  }

  @Test
  void systemMetrics() throws Exception {
    mvc.perform(
            get("/api/v1/admin/metrics/system").header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk());
  }

  @Test
  void funnelDefaultWindow() throws Exception {
    mvc.perform(get("/api/v1/admin/funnel").header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.users").isNumber());
  }

  @Test
  void funnelSpecificWindow() throws Exception {
    mvc.perform(
            get("/api/v1/admin/funnel")
                .param("window", "30d")
                .header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk());
  }
}
