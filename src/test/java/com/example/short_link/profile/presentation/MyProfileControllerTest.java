package com.example.short_link.profile.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class MyProfileControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @Test
  void anonymousIsRejected() throws Exception {
    mvc.perform(get("/api/v1/users/me/profile")).andExpect(status().isUnauthorized());
  }

  @Test
  void getReturnsCurrentProfile() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("m@x.com", "google", "g-myp"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(get("/api/v1/users/me/profile").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").doesNotExist());
  }

  @Test
  void updateAcceptsValidUsernameAndBio() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-upd"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newname\",\"bio\":\"hi\",\"theme\":\"light\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("newname"))
        .andExpect(jsonPath("$.bio").value("hi"));
  }

  @Test
  void updateRejectsInvalidUsername() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-rej"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"BAD NAME\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateRejectsReservedUsername() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-res"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void reorderEmptyListReturnsCurrentProfile() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-ord"));
    String token = jwt.createAccessToken(user.getId(), "USER");

    mvc.perform(
            put("/api/v1/users/me/profile/order")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
        .andExpect(status().isOk());
  }

  @Test
  void reorderAnonymousIs401() throws Exception {
    mvc.perform(
            put("/api/v1/users/me/profile/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
        .andExpect(status().isUnauthorized());
  }
}
