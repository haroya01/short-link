package com.example.short_link.admin.api;

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
class AdminControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private ClickEventRepository clickRepository;

  @Test
  void anonymousReceives401OnAdminEndpoint() throws Exception {
    mvc.perform(get("/api/v1/admin/overview")).andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserReceives403OnAdminEndpoint() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("user@x.com", "google", "g-u"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReceivesOverviewWithKpis() throws Exception {
    UserEntity admin = userRepository.save(new UserEntity("admin@x.com", "google", "g-a"));
    admin.promoteToAdmin();
    userRepository.save(admin);

    LinkEntity link =
        linkRepository.save(new LinkEntity("https://example.com", "adov001", admin.getId(), null));
    clickRepository.save(
        ClickEventEntity.builder()
            .linkId(link.getId())
            .userAgent("ua")
            .clientIp("1.1.1.1")
            .deviceClass("desktop")
            .bot(false)
            .build());
    String token = jwt.createAccessToken(admin.getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.users").isNumber())
        .andExpect(jsonPath("$.totals.links").isNumber())
        .andExpect(jsonPath("$.totals.clicks").isNumber())
        .andExpect(jsonPath("$.dailySignups").isArray())
        .andExpect(jsonPath("$.dailyLinks").isArray())
        .andExpect(jsonPath("$.dailyClicks").isArray())
        .andExpect(jsonPath("$.topUsersByLinks").isArray())
        .andExpect(jsonPath("$.topUsersByClicks").isArray())
        .andExpect(jsonPath("$.topLinksByClicks").isArray());
  }
}
