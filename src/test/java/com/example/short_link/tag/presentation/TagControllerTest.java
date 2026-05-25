package com.example.short_link.tag.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.tag.application.TagService;
import com.example.short_link.tag.application.TagService.TagSummary;
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
class TagControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private TagService tagService;

  @Test
  void anonymousListIs401() throws Exception {
    mvc.perform(get("/api/v1/tags")).andExpect(status().isUnauthorized());
  }

  @Test
  void listReturnsOwnerTagsOnly() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("t@x.com", "google", "g-tagL"));
    UserEntity other = userRepository.save(new UserEntity("o@x.com", "google", "g-tagO"));
    tagService.create(owner.getId(), "mine", "#aabbcc");
    tagService.create(other.getId(), "theirs", "#112233");
    String token = jwt.createAccessToken(owner.getId(), "USER");

    mvc.perform(get("/api/v1/tags").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("mine"))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void createTag() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-tagC"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/tags")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"work\",\"color\":\"#ff0000\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("work"));
  }

  @Test
  void createRejectsInvalidColor() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-tagIV"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            post("/api/v1/tags")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"color\":\"red\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateTag() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-tagU"));
    TagSummary tag = tagService.create(user.getId(), "old", "#000000");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            patch("/api/v1/tags/" + tag.id())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"new\",\"color\":\"#ffffff\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("new"));
  }

  @Test
  void updateOthersTagReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-tagOw"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-tagAt"));
    TagSummary tag = tagService.create(owner.getId(), "owners", "#aaaaaa");
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");

    mvc.perform(
            patch("/api/v1/tags/" + tag.id())
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hax\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteTag() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-tagD"));
    TagSummary tag = tagService.create(user.getId(), "doomed", "#cccccc");
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(delete("/api/v1/tags/" + tag.id()).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  @Test
  void deleteOthersTagReturns404() throws Exception {
    UserEntity owner = userRepository.save(new UserEntity("o@x.com", "google", "g-tagDo"));
    UserEntity attacker = userRepository.save(new UserEntity("a@x.com", "google", "g-tagDa"));
    TagSummary tag = tagService.create(owner.getId(), "owners", "#aaaaaa");
    String attackerToken = jwt.createAccessToken(attacker.getId(), "USER");

    mvc.perform(
            delete("/api/v1/tags/" + tag.id()).header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isNotFound());
  }
}
