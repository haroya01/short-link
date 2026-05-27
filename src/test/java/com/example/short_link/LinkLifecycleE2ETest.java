package com.example.short_link;

import static com.example.short_link.support.TestCacheCleaner.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class LinkLifecycleE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void clearLinkCache() {
    clear(cacheManager, "link");
  }

  @Test
  void updateInvalidatesCache_redirectFollowsNewUrl() throws Exception {
    // given
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-e1"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    // when
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://old.com\",\"customCode\":\"e2e0001\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/e2e0001"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://old.com"));

    mvc.perform(
            patch("/api/v1/links/e2e0001")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://new.com\"}"))
        .andExpect(status().isOk());

    // then
    mvc.perform(get("/e2e0001"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://new.com"));
  }

  @Test
  void deleteInvalidatesCache_redirectReturns404() throws Exception {
    // given
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-e2"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    // when
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"e2e0002\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/e2e0002")).andExpect(status().isFound());

    mvc.perform(delete("/api/v1/links/e2e0002").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    // then
    mvc.perform(get("/e2e0002")).andExpect(status().isNotFound());
  }

  @Test
  void disableViaPastExpiresAt_redirectReturns410() throws Exception {
    // given
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-e3"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"e2e0003\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/e2e0003")).andExpect(status().isFound());

    // when
    Instant past = Instant.now().minusSeconds(60);
    mvc.perform(
            patch("/api/v1/links/e2e0003")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"" + past + "\"}"))
        .andExpect(status().isOk());

    // then
    mvc.perform(get("/e2e0003")).andExpect(status().isGone());
  }

  @Test
  void anonymousLinkCanBeRedirectedButNotManaged() throws Exception {
    // given
    String body =
        mvc.perform(
                post("/api/v1/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://anon.com\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String shortCode = JsonPath.read(body, "$.shortCode");

    // when
    mvc.perform(get("/" + shortCode)).andExpect(status().isFound());

    // then
    mvc.perform(
            patch("/api/v1/links/" + shortCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://hijack.com\"}"))
        .andExpect(status().isUnauthorized());

    mvc.perform(delete("/api/v1/links/" + shortCode)).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedUserSeesOnlyOwnLinksInMyList() throws Exception {
    // given
    UserEntity me = userRepository.save(new UserEntity("me@x.com", "google", "g-e5"));
    UserEntity other = userRepository.save(new UserEntity("other@x.com", "google", "g-e5o"));
    String myToken = jwt.createAccessToken(me.getId(), "USER");
    String otherToken = jwt.createAccessToken(other.getId(), "USER");

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + myToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://mine.com\",\"customCode\":\"e2e0005\"}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://yours.com\",\"customCode\":\"e2e0005o\"}"))
        .andExpect(status().isCreated());

    // when
    String body =
        mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + myToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // then
    List<?> items = JsonPath.read(body, "$.items");
    assertThat(items).hasSize(1);
    boolean hasMore = JsonPath.read(body, "$.hasMore");
    assertThat(hasMore).isFalse();
    String shortCode = JsonPath.read(body, "$.items[0].shortCode");
    assertThat(shortCode).isEqualTo("e2e0005");
  }
}
