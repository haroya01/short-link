package com.example.short_link.link.stats.presentation;

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
class LinkEventsControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void returnsLatestEventsForOwnerWithMaskedIp() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-evt1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "evt001", owner.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.2.3.4")
            .deviceClass("desktop")
            .countryCode("KR")
            .cityName("Seoul")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(get("/api/v1/links/evt001/events").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].country").value("KR"))
        .andExpect(jsonPath("$.items[0].city").value("Seoul"))
        .andExpect(jsonPath("$.items[0].ipMasked").value("1.2.3.*"))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void rejectsNonOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-evt2"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-evt2a"));
    linkRepository.save(new LinkEntity("https://example.com", "evt002", owner.getId(), null));
    String token = jwt.createAccessToken(attacker.getId(), "USER");

    mvc.perform(get("/api/v1/links/evt002/events").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAnonymous() throws Exception {
    mvc.perform(get("/api/v1/links/evt003/events")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidCursor() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-evt4"));
    linkRepository.save(new LinkEntity("https://example.com", "evt004", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(
            get("/api/v1/links/evt004/events")
                .header("Authorization", "Bearer " + token)
                .param("cursor", "@@@not-base64@@@"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
  }

  @Test
  void paginatesWithCursor() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-evt5"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "evt005", owner.getId(), null));
    for (int i = 0; i < 5; i++) {
      clickRepository.save(
          ClickEventEntity.builder()
              .linkId(link.getId())
              .userAgent("ua")
              .clientIp("1.2.3." + i)
              .deviceClass("desktop")
              .bot(false)
              .build());
    }
    String token = jwt.createAccessToken(owner.getId(), "USER");

    var first =
        mvc.perform(
                get("/api/v1/links/evt005/events")
                    .header("Authorization", "Bearer " + token)
                    .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.nextCursor").isString())
            .andReturn();
    String cursor =
        com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(first.getResponse().getContentAsString())
            .get("nextCursor")
            .asText();

    mvc.perform(
            get("/api/v1/links/evt005/events")
                .header("Authorization", "Bearer " + token)
                .param("limit", "2")
                .param("cursor", cursor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.nextCursor").isString());
  }
}
