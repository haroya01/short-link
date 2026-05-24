package com.example.short_link.user.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.avatar.AvatarService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import com.example.short_link.user.exception.AvatarUnavailableException;
import com.example.short_link.user.exception.InvalidAvatarException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvatarControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private AvatarService service;

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/avatar/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void presignOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-av"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.presignUpload(eq(user.getId()), eq("image/jpeg")))
        .thenReturn(
            new AvatarService.PresignResult(
                "https://signed",
                "https://cdn/avatars/1/x.jpg",
                "avatars/1/x.jpg",
                "image/jpeg",
                5000,
                300));

    mvc.perform(
            post("/api/v1/users/me/avatar/presigned-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadUrl").value("https://signed"));
  }

  @Test
  void presignInvalidContentTypeReturns400() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-avi"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.presignUpload(anyLong(), eq("image/gif")))
        .thenThrow(new InvalidAvatarException("contentType not allowed"));

    mvc.perform(
            post("/api/v1/users/me/avatar/presigned-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/gif\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void presignWhenS3NotConfiguredReturns503() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-avu"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.presignUpload(anyLong(), eq("image/jpeg")))
        .thenThrow(new AvatarUnavailableException());

    mvc.perform(
            post("/api/v1/users/me/avatar/presigned-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void commitOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-avc"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.commitUpload(eq(user.getId()), eq("avatars/1/x.jpg")))
        .thenReturn(new AvatarService.CommitResult("https://cdn/avatars/1/x.jpg"));

    mvc.perform(
            put("/api/v1/users/me/avatar")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"avatars/1/x.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.avatarUrl").value("https://cdn/avatars/1/x.jpg"));
  }

  @Test
  void clearOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-avd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(service).clearAvatar(eq(user.getId()));

    mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void clearWhenS3NotConfiguredReturns503() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("ds@x.com", "google", "g-avds"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doThrow(new AvatarUnavailableException()).when(service).clearAvatar(eq(user.getId()));

    mvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", "Bearer " + token))
        .andExpect(status().isServiceUnavailable());
  }
}
