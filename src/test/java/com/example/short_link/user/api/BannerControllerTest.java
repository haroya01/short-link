package com.example.short_link.user.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.application.avatar.BannerService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
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
class BannerControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private BannerService service;

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(
            post("/api/v1/users/me/banner/presigned-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void presignOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("u@x.com", "google", "g-bn"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.presignUpload(eq(user.getId()), eq("image/jpeg")))
        .thenReturn(
            new BannerService.PresignResult(
                "https://signed",
                "https://cdn/banners/1/x.jpg",
                "banners/1/x.jpg",
                "image/jpeg",
                9000,
                300));

    mvc.perform(
            post("/api/v1/users/me/banner/presigned-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploadUrl").value("https://signed"));
  }

  @Test
  void commitOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("c@x.com", "google", "g-bnc"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.commitUpload(eq(user.getId()), eq("banners/1/x.jpg")))
        .thenReturn(new BannerService.CommitResult("https://cdn/banners/1/x.jpg"));

    mvc.perform(
            put("/api/v1/users/me/banner")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"banners/1/x.jpg\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bannerUrl").value("https://cdn/banners/1/x.jpg"));
  }

  @Test
  void clearOk() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("d@x.com", "google", "g-bnd"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    doNothing().when(service).clearBanner(eq(user.getId()));

    mvc.perform(delete("/api/v1/users/me/banner").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
