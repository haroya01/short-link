package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.infrastructure.persistence.JpaBlockedDomainRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OutboundUrlSafetyE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JpaBlockedDomainRepository blockedDomains;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void aBlockedDomain() {
    blockedDomains.saveAndFlush(new BlockedDomainEntity("phish.test", "direct", 1L));
    clear(cacheManager, "blocked-domains");
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "https://phish.test/login"})
  void aDestinationAShortLinkWouldRefuseIsRefusedAsACtaToo(String url) throws Exception {
    UserEntity owner = newUser();

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/ctas")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"go\",\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"javascript:alert(1)", "https://phish.test/login"})
  void aCtaCannotBeMovedToADestinationAShortLinkWouldRefuse(String url) throws Exception {
    UserEntity owner = newUser();
    String created =
        mvc.perform(
                post("/api/v1/ctas")
                    .header("Authorization", bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"label\":\"go\",\"url\":\"https://example.com/\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.id")).longValue();

    mvc.perform(
            patch("/api/v1/ctas/" + id)
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @org.junit.jupiter.api.Test
  void aProductCardsBuyButtonCannotPointAtABlockedDomain() throws Exception {
    UserEntity owner = newUser();
    String fine =
        "{\\\"items\\\":[{\\\"name\\\":\\\"Bread\\\",\\\"ctaUrl\\\":\\\"https://example.com/buy\\\"}]}";
    String phish = fine.replace("https://example.com/buy", "https://phish.test/buy");

    String created =
        mvc.perform(
                post("/api/v1/users/me/profile/blocks")
                    .header("Authorization", bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"PRODUCT_CARD\",\"content\":\"" + fine + "\"}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.id")).longValue();

    mvc.perform(
            post("/api/v1/users/me/profile/blocks")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PRODUCT_CARD\",\"content\":\"" + phish + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/users/me/profile/blocks/" + id)
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + phish + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"locationUrl", "onlineUrl"})
  void anEventsPlaceOrOnlineLinkCannotPointAtABlockedDomain(String field) throws Exception {
    UserEntity owner = newUser();
    String event =
        "{\"title\":\"study\",\"startsAt\":\"2030-01-01T10:00:00Z\",\"timezone\":\"Asia/Seoul\"";

    mvc.perform(
            post("/api/v1/events")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(event + ",\"" + field + "\":\"https://phish.test/room\"}"))
        .andExpect(status().isBadRequest());

    String created =
        mvc.perform(
                post("/api/v1/events")
                    .header("Authorization", bearer(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(event + "}"))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = ((Number) JsonPath.read(created, "$.id")).longValue();
    mvc.perform(
            patch("/api/v1/events/" + id)
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + field + "\":\"https://phish.test/room\"}"))
        .andExpect(status().isBadRequest());
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
