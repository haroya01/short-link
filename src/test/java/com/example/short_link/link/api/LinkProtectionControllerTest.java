package com.example.short_link.link.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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
class LinkProtectionControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(
            patch("/api/v1/links/abc1234/protection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"hunter2\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ownerCanSetPasswordAndMaxViews() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-prot"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "prt0001", user.getId(), null));

    mvc.perform(
            patch("/api/v1/links/" + link.getShortCode() + "/protection")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"hunter2\",\"maxViews\":10}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passwordProtected").value(true))
        .andExpect(jsonPath("$.maxViews").value(10));
  }

  @Test
  void nonOwnerIsForbidden() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-prot-o"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-prot-a"));
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "prt0002", owner.getId(), null));

    mvc.perform(
            patch("/api/v1/links/" + link.getShortCode() + "/protection")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"hack\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidMaxViewsBelowOneReturns400() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-prot-iv"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "prt0003", user.getId(), null));

    mvc.perform(
            patch("/api/v1/links/" + link.getShortCode() + "/protection")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxViews\":0}"))
        .andExpect(status().isBadRequest());
  }
}
