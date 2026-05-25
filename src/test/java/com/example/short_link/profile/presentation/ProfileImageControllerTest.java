package com.example.short_link.profile.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.application.image.ProfileImageService;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
class ProfileImageControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private ProfileImageService imageService;

  @Test
  void anonymousPresignIs401() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/profile/images/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void presignReturnsServiceResult() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("p@x.com", "google", "g-pres"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(imageService.presignUpload(eq(user.getId()), eq("image/jpeg")))
        .thenReturn(
            new ProfileImageService.PresignResult(
                "https://signed", "https://cdn/profile-images/k", "k", "image/jpeg", 1000, 300));

    mvc.perform(
            post("/api/v1/users/me/profile/images/presigned-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadUrl").value("https://signed"))
        .andExpect(jsonPath("$.key").value("k"));
  }

  @Test
  void commitReturnsServiceResult() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-com"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(imageService.commitUpload(eq(user.getId()), eq("profile-images/1/x.jpg")))
        .thenReturn(
            new ProfileImageService.CommitResult("https://cdn/x.jpg", "profile-images/1/x.jpg"));

    mvc.perform(
            put("/api/v1/users/me/profile/images")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"profile-images/1/x.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageUrl").value("https://cdn/x.jpg"));
  }
}
