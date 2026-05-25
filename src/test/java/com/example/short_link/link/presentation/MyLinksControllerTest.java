package com.example.short_link.link.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
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
class MyLinksControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void returnsOnlyOwnerLinks() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("me@example.com", "google", "g-me"));
    UserEntity other = userRepository.save(new UserEntity("o@example.com", "google", "g-other"));
    linkRepository.save(new LinkEntity("https://example.com/mine", "mine001", owner.getId(), null));
    linkRepository.save(
        new LinkEntity("https://example.com/other", "other01", other.getId(), null));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].shortCode").value("mine001"))
        .andExpect(jsonPath("$.items[0].originalUrl").value("https://example.com/mine"))
        .andExpect(jsonPath("$.items[0].shortUrl").value("http://localhost:8080/mine001"));
  }

  @Test
  void paginatesResults() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("p@x.com", "google", "g-page"));
    for (int i = 0; i < 25; i++) {
      String code = String.format("page%03d", i);
      linkRepository.save(new LinkEntity("https://example.com/p" + i, code, owner.getId(), null));
    }
    String token = jwt.createAccessToken(owner.getId(), "USER");

    String firstResponse =
        mvc.perform(
                get("/api/v1/links/me")
                    .header("Authorization", "Bearer " + token)
                    .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(10))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstCursor =
        com.jayway.jsonpath.JsonPath.read(firstResponse, "$.nextCursor").toString();

    String secondResponse =
        mvc.perform(
                get("/api/v1/links/me")
                    .header("Authorization", "Bearer " + token)
                    .param("size", "10")
                    .param("after", firstCursor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(10))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String secondCursor =
        com.jayway.jsonpath.JsonPath.read(secondResponse, "$.nextCursor").toString();

    mvc.perform(
            get("/api/v1/links/me")
                .header("Authorization", "Bearer " + token)
                .param("size", "10")
                .param("after", secondCursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void searchesByPartialMatchOnUrlOrCode() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("s@x.com", "google", "g-search"));
    linkRepository.save(
        new LinkEntity("https://example.com/marketing", "MKT0001", owner.getId(), null));
    linkRepository.save(
        new LinkEntity("https://example.com/other", "BLOG002", owner.getId(), null));
    linkRepository.save(
        new LinkEntity("https://other.org/marketing-page", "OTH0003", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/me")
                .header("Authorization", "Bearer " + token)
                .param("q", "marketing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));

    mvc.perform(
            get("/api/v1/links/me").header("Authorization", "Bearer " + token).param("q", "BLOG"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].shortCode").value("BLOG002"));
  }

  @Test
  void unauthorizedWithoutToken() throws Exception {
    mvc.perform(get("/api/v1/links/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsRefreshTokenAsAccess() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("x@example.com", "google", "g-x"));
    String refresh = jwt.createRefreshToken(user.getId()).token();

    mvc.perform(get("/api/v1/links/me").header("Authorization", "Bearer " + refresh))
        .andExpect(status().isUnauthorized());
  }
}
