package com.example.short_link.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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
class LinkStatsControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void returnsTotalClicksForOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s1"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats01", owner.getId(), null));
    for (int i = 0; i < 3; i++) {
      clickRepository.save(new ClickEventEntity(link.getId(), null, "Mozilla/5.0", "1.2.3.4"));
    }
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats01/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").value("stats01"))
        .andExpect(jsonPath("$.totalClicks").value(3));
  }

  @Test
  void aggregatesByDevice() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s2"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats02", owner.getId(), null));
    clickRepository.save(
        new ClickEventEntity(
            link.getId(), null, "Mozilla/5.0 (iPhone; CPU iPhone OS) Mobile", "1.1.1.1"));
    clickRepository.save(
        new ClickEventEntity(link.getId(), null, "Mozilla/5.0 (iPad; CPU OS) Tablet", "1.1.1.2"));
    clickRepository.save(
        new ClickEventEntity(
            link.getId(), null, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", "1.1.1.3"));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats02/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceClicks").isArray())
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'mobile')].count").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'tablet')].count").value(1))
        .andExpect(jsonPath("$.deviceClicks[?(@.device == 'desktop')].count").value(1));
  }

  @Test
  void aggregatesByReferrer() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s3"));
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "stats03", owner.getId(), null));
    clickRepository.save(
        new ClickEventEntity(link.getId(), "https://instagram.com", "ua", "1.1.1.1"));
    clickRepository.save(
        new ClickEventEntity(link.getId(), "https://instagram.com", "ua", "1.1.1.2"));
    clickRepository.save(
        new ClickEventEntity(link.getId(), "https://youtube.com", "ua", "1.1.1.3"));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(get("/api/v1/links/stats03/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.referrerClicks[?(@.referrer == 'https://instagram.com')].count").value(2))
        .andExpect(
            jsonPath("$.referrerClicks[?(@.referrer == 'https://youtube.com')].count").value(1));
  }

  @Test
  void rejectsStatsForNotOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s4"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-s4a"));
    linkRepository.save(new LinkEntity("https://example.com", "stats04", owner.getId(), null));
    String attackerToken = jwt.createAccessToken(attacker.getId());

    mvc.perform(
            get("/api/v1/links/stats04/stats").header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LINK_NOT_OWNED"));
  }

  @Test
  void rejectsStatsForUnknownCode() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-s5"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(get("/api/v1/links/missing/stats").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsStatsWhenAnonymous() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-s6"));
    linkRepository.save(new LinkEntity("https://example.com", "stats06", owner.getId(), null));

    mvc.perform(get("/api/v1/links/stats06/stats")).andExpect(status().isUnauthorized());
  }
}
