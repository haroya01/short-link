package com.example.short_link.profile.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LinkProfileToggleControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;

  @Test
  void anonymousToggleIs401() throws Exception {
    mvc.perform(
            put("/api/v1/links/abc/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\":true}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void toggleShowsLinkOnProfile() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-tg"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00001", user.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.show").value(true));
  }

  @Test
  void toggleHidesLinkFromProfile() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("h@x.com", "google", "g-th"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00002", user.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.show").value(false));
  }

  @Test
  void toggleOnNonOwnedLinkReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-to"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-ta"));
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00003", owner.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"show\":true}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void setHighlight() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("hi@x.com", "google", "g-hi"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00004", user.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile/highlight")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"highlighted\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.highlighted").value(true));
  }

  @Test
  void unsetHighlight() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("hu@x.com", "google", "g-hu"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00005", user.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile/highlight")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"highlighted\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.highlighted").value(false));
  }

  @Test
  void highlightOnNonOwnedLinkReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-ho"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-ha"));
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "tg00006", owner.getId(), null));

    mvc.perform(
            put("/api/v1/links/" + link.getShortCode() + "/profile/highlight")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"highlighted\":true}"))
        .andExpect(status().isNotFound());
  }
}
