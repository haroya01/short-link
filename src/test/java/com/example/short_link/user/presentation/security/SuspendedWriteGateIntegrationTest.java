package com.example.short_link.user.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SuspendedWriteGateIntegrationTest {

  private static final Map<String, String> ONE_WRITE_PER_MODULE =
      Map.of(
          "/api/v1/links", "{\"url\":\"https://example.com/gate\"}",
          "/api/v1/events", "{}",
          "/api/v1/rooms", "{\"name\":\"gate\"}",
          "/api/v1/ctas", "{}",
          "/api/v1/campaigns", "{}",
          "/api/v1/custom-domains", "{}");

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository users;
  @Autowired private JwtTokenService jwt;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Test
  void aSuspendedOrBannedAccountWritesNothingInAnyGatedModule() throws Exception {
    UserEntity suspended = newUser();
    suspended.suspend(Instant.now().plus(Duration.ofDays(1)));
    users.save(suspended);
    UserEntity banned = newUser();
    banned.ban();
    users.save(banned);

    for (Map.Entry<String, String> write : ONE_WRITE_PER_MODULE.entrySet()) {
      mvc.perform(
              post(write.getKey())
                  .header("Authorization", bearer(suspended))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(write.getValue()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
      mvc.perform(
              post(write.getKey())
                  .header("Authorization", bearer(banned))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(write.getValue()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
    }
  }

  @Test
  void readsAndAnAccountInGoodStandingAreLetThrough() throws Exception {
    UserEntity suspended = newUser();
    suspended.suspend(Instant.now().plus(Duration.ofDays(1)));
    users.save(suspended);
    UserEntity fine = newUser();

    mvc.perform(get("/api/v1/rooms").header("Authorization", bearer(suspended)))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", bearer(fine))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/fine\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/anonymous\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void everyWriteHandlerInTheGatedModulesIsGatedButTheKnownReads() {
    Set<String> open = new TreeSet<>();
    int gated = 0;
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
        mappings.getHandlerMethods().entrySet()) {
      HandlerMethod method = entry.getValue();
      boolean writes =
          entry.getKey().getMethodsCondition().getMethods().stream()
              .anyMatch(m -> Set.of("POST", "PUT", "PATCH", "DELETE").contains(m.name()));
      if (!writes || !SuspendedWriteGate.inGatedModule(method.getBeanType())) {
        continue;
      }
      if (SuspendedWriteGate.gated(method)) {
        gated++;
      } else {
        open.add(method.getBeanType().getSimpleName() + "." + method.getMethod().getName());
      }
    }

    assertThat(open)
        .containsExactly(
            "CampaignStatsCompareController.compare",
            "MyClickStreamController.issueStreamToken",
            "PasswordUnlockController.unlock",
            "SseClickStreamController.issueStreamToken");
    assertThat(gated).isGreaterThan(60);
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return users.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
