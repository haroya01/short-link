package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

  private static final String SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko)"
          + " Version/17.0 Safari/605.1.15";

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

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https:///phish.test/login",
        "https:////phish.test/login",
        "https://phish.test./login",
        "https://a_b.phish.test/login",
        "https://phish%2Etest/login",
        "https://%70hish.test/login",
        "https://\uff50hish.test/login",
        "https://phish\u3002test/login"
      })
  void aBlockedDomainWrittenAnotherWayABrowserStillOpensIsRefused(String url) throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", bearer(newUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALICIOUS_URL"));
  }

  @org.junit.jupiter.api.Test
  void aLinkWrittenAnotherWayStopsRedirectingOnceItsDomainIsBlocked() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/links")
                    .header("Authorization", bearer(newUser()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https:///later.test/login\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String code = JsonPath.read(created, "$.shortCode");

    blockedDomains.saveAndFlush(new BlockedDomainEntity("later.test", "direct", 1L));
    clear(cacheManager, "blocked-domains");
    clear(cacheManager, "link");

    mvc.perform(get("/" + code).header("User-Agent", SAFARI)).andExpect(status().isForbidden());
  }

  @org.junit.jupiter.api.Test
  void aLinkBackToThisServiceWrittenAnotherWayIsStillASelfReference() throws Exception {
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", bearer(newUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https:///localhost/abc1234\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_REFERENCING_URL"));
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
