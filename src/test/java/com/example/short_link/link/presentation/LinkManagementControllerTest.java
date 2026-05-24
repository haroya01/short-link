package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class LinkManagementControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenService jwt;

  @Test
  void updatesOriginalUrlWhenOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo1"));
    linkRepository.save(new LinkEntity("https://old.com", "upd0001", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0001")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://new.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalUrl").value("https://new.com"))
        .andExpect(jsonPath("$.shortCode").value("upd0001"));
  }

  @Test
  void updatesExpiresAtWhenOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo2"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0002", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0002")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void updatesBothFieldsWhenOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo3"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0003", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0003")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"originalUrl\":\"https://both.com\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalUrl").value("https://both.com"))
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void noOpWhenBothFieldsNull() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo4"));
    linkRepository.save(new LinkEntity("https://stable.com", "upd0004", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0004")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalUrl").value("https://stable.com"));
  }

  @Test
  void rejectsUpdateWhenNotOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo5"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-att"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0005", owner.getId(), null));
    String attackerToken = jwt.createAccessToken(attacker.getId());

    mvc.perform(
            patch("/api/v1/links/upd0005")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://hijack.com\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LINK_NOT_OWNED"));
  }

  @Test
  void rejectsUpdateForAnonymousLink() throws Exception {
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-uo6"));
    linkRepository.save(new LinkEntity("https://anon.com", "upd0006", null, null));
    String token = jwt.createAccessToken(attacker.getId());

    mvc.perform(
            patch("/api/v1/links/upd0006")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://hijack.com\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsUpdateWhenAnonymous() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo7"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0007", owner.getId(), null));

    mvc.perform(
            patch("/api/v1/links/upd0007")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://new.com\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsUpdateForUnknownCode() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-uo8"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(
            patch("/api/v1/links/missing")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://new.com\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("LINK_NOT_FOUND"));
  }

  @Test
  void rejectsUpdateWithInvalidUrl() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uo9"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0009", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0009")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"not-a-url\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsUpdateWithEmptyUrl() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-uem"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0011", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0011")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void rejectsUpdateWithJavascriptScheme() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-u10"));
    linkRepository.save(new LinkEntity("https://example.com", "upd0010", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(
            patch("/api/v1/links/upd0010")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deletesWhenOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-d1"));
    linkRepository.save(new LinkEntity("https://example.com", "del0001", owner.getId(), null));
    String token = jwt.createAccessToken(owner.getId());

    mvc.perform(delete("/api/v1/links/del0001").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    assertThat(linkRepository.findByShortCode("del0001")).isEmpty();
  }

  @Test
  void rejectsDeleteWhenNotOwner() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-d2"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-d2a"));
    linkRepository.save(new LinkEntity("https://example.com", "del0002", owner.getId(), null));
    String attackerToken = jwt.createAccessToken(attacker.getId());

    mvc.perform(delete("/api/v1/links/del0002").header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isForbidden());
    assertThat(linkRepository.findByShortCode("del0002")).isPresent();
  }

  @Test
  void rejectsDeleteWhenAnonymous() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-d3"));
    linkRepository.save(new LinkEntity("https://example.com", "del0003", owner.getId(), null));

    mvc.perform(delete("/api/v1/links/del0003")).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsDeleteForUnknownCode() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-d4"));
    String token = jwt.createAccessToken(user.getId());

    mvc.perform(delete("/api/v1/links/missing").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }
}
