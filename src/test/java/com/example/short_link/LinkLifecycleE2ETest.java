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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
/** 링크 캐시는 커밋 뒤에 지워지므로, 캐시 무효화를 보는 테스트는 테스트 트랜잭션 없이 요청마다 커밋한다. */
@ActiveProfiles("test")
@Transactional
class LinkLifecycleE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;
  @Autowired private CacheManager cacheManager;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> createdUsers = new ArrayList<>();

  @BeforeEach
  void clearLinkCache() {
    clear(cacheManager, "link");
  }

  @AfterEach
  void deleteCommittedRows() {
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void updateInvalidatesCache_redirectFollowsNewUrl() throws Exception {
    // given
    String code = uniqueCode();
    UserEntity user = newUser();
    String token = jwt.createAccessToken(user.getId(), "USER");

    // when
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://old.com\",\"customCode\":\"" + code + "\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/" + code))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://old.com"));

    mvc.perform(
            patch("/api/v1/links/" + code)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://new.com\"}"))
        .andExpect(status().isOk());

    // then
    mvc.perform(get("/" + code))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://new.com"));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void deleteInvalidatesCache_redirectReturns404() throws Exception {
    // given
    String code = uniqueCode();
    UserEntity user = newUser();
    String token = jwt.createAccessToken(user.getId(), "USER");

    // when
    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"" + code + "\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/" + code)).andExpect(status().isFound());

    mvc.perform(delete("/api/v1/links/" + code).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    // then
    mvc.perform(get("/" + code)).andExpect(status().isNotFound());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void disableViaPastExpiresAt_redirectReturns410() throws Exception {
    // given
    String code = uniqueCode();
    UserEntity user = newUser();
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/links")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com\",\"customCode\":\"" + code + "\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/" + code)).andExpect(status().isFound());

    // when
    Instant past = Instant.now().minusSeconds(60);
    mvc.perform(
            patch("/api/v1/links/" + code)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"" + past + "\"}"))
        .andExpect(status().isOk());

    // then
    mvc.perform(get("/" + code)).andExpect(status().isGone());
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    UserEntity user = userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
    createdUsers.add(user.getId());
    return user;
  }

  private static String uniqueCode() {
    return "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
