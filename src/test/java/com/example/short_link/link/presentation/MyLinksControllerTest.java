package com.example.short_link.link.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
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
  @Autowired private ClickEventRepository clickRepository;
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
    String firstCursor = JsonPath.read(firstResponse, "$.nextCursor").toString();

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
    String secondCursor = JsonPath.read(secondResponse, "$.nextCursor").toString();

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
  void sortsByClickCountAcrossFullFilteredSet() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("sort@x.com", "google", "g-sort"));
    LinkEntity zero =
        linkRepository.save(
            new LinkEntity("https://example.com/zero", "sort000", owner.getId(), null));
    LinkEntity one =
        linkRepository.save(
            new LinkEntity("https://example.com/one", "sort001", owner.getId(), null));
    LinkEntity three =
        linkRepository.save(
            new LinkEntity("https://example.com/three", "sort003", owner.getId(), null));
    click(one, 1);
    click(three, 3);
    String token = jwt.createAccessToken(owner.getId(), "USER");

    String firstResponse =
        mvc.perform(
                get("/api/v1/links/me")
                    .header("Authorization", "Bearer " + token)
                    .param("sort", "clickCount")
                    .param("dir", "desc")
                    .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].shortCode").value("sort003"))
            .andExpect(jsonPath("$.items[0].clickCount").value(3))
            .andExpect(jsonPath("$.items[1].shortCode").value("sort001"))
            .andExpect(jsonPath("$.items[1].clickCount").value(1))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(firstResponse, "$.nextCursor").toString();

    mvc.perform(
            get("/api/v1/links/me")
                .header("Authorization", "Bearer " + token)
                .param("sort", "clickCount")
                .param("dir", "desc")
                .param("size", "2")
                .param("after", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].shortCode").value("sort000"))
        .andExpect(jsonPath("$.items[0].clickCount").value(0))
        .andExpect(jsonPath("$.hasMore").value(false));
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

  @Test
  void sortsByClickCountAscendingMovesZeroFirst() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("sasc@x.com", "google", "g-sasc"));
    LinkEntity high =
        linkRepository.save(
            new LinkEntity("https://example.com/h", "asc0001", owner.getId(), null));
    LinkEntity low =
        linkRepository.save(
            new LinkEntity("https://example.com/l", "asc0002", owner.getId(), null));
    click(high, 5);
    click(low, 1);
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/me")
                .header("Authorization", "Bearer " + token)
                .param("sort", "clickCount")
                .param("dir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].shortCode").value("asc0002"))
        .andExpect(jsonPath("$.items[1].shortCode").value("asc0001"));
  }

  @Test
  void emptyResultByClickCountSort() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("emp@x.com", "google", "g-emp"));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/me")
                .header("Authorization", "Bearer " + token)
                .param("sort", "clickCount"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.hasMore").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void sortsByCreatedAtAscendingDir() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("ca@x.com", "google", "g-ca"));
    LinkEntity early =
        linkRepository.save(
            new LinkEntity("https://example.com/e", "cae0001", owner.getId(), null));
    linkRepository.save(new LinkEntity("https://example.com/l", "cal0001", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/me").header("Authorization", "Bearer " + token).param("dir", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].shortCode").value(early.getShortCode().value()));
  }

  private void click(LinkEntity link, int count) {
    for (int i = 0; i < count; i++) {
      clickRepository.save(
          ClickEventEntity.builder()
              .linkId(link.linkId())
              .clickedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i))
              .bot(false)
              .build());
    }
  }
}
