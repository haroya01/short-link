package com.example.short_link.profile.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
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
class ProfileBlockControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private ProfileBlockRepository blockRepository;

  @Test
  void anonymousIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/profile/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DIVIDER\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createDividerBlock() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("b@x.com", "google", "g-blk"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/users/me/profile/blocks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"DIVIDER\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("DIVIDER"))
        .andExpect(jsonPath("$.id").isNumber());
  }

  @Test
  void updateBlockContent() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("b@x.com", "google", "g-blk2"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.TEXT, "{\"body\":\"old\"}", 1));

    mvc.perform(
            patch("/api/v1/users/me/profile/blocks/" + block.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"new body\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(block.getId()));
  }

  @Test
  void updateBlockOfAnotherUserReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-owner"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-att"));
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(owner.getId(), ProfileBlockType.TEXT, "{\"body\":\"o\"}", 1));

    mvc.perform(
            patch("/api/v1/users/me/profile/blocks/" + block.getId())
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"hax\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateDividerRejected() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-div"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.DIVIDER, null, 1));

    mvc.perform(
            patch("/api/v1/users/me/profile/blocks/" + block.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"anything\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteBlock() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-del"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(user.getId(), ProfileBlockType.DIVIDER, null, 1));

    mvc.perform(
            delete("/api/v1/users/me/profile/blocks/" + block.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void deleteBlockOfAnotherUserReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-do"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-da"));
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");
    ProfileBlockEntity block =
        blockRepository.save(
            new ProfileBlockEntity(owner.getId(), ProfileBlockType.DIVIDER, null, 1));

    mvc.perform(
            delete("/api/v1/users/me/profile/blocks/" + block.getId())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound());
  }
}
