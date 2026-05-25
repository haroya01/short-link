package com.example.short_link.profile.presentation.visit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.profile.application.visit.ProfileVisitRecorder;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicProfileVisitControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;

  @MockitoBean private ProfileVisitRecorder recorder;

  @Test
  void unknownUserReturns404() throws Exception {
    doThrow(new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, "nobody"))
        .when(recorder)
        .recordUsername(
            eq("nobody"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    mvc.perform(post("/api/v1/public/profiles/nobody/visit")).andExpect(status().isNotFound());
  }

  @Test
  void recordsVisitFor204() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("v@x.com", "google", "g-pv"));
    user.claimUsername("visituser");
    userRepository.save(user);
    doNothing()
        .when(recorder)
        .recordUsername(
            eq("visituser"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    mvc.perform(
            post("/api/v1/public/profiles/visituser/visit")
                .header("Referer", "https://t.co/x")
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Language", "ko-KR")
                .header("X-Forwarded-For", "203.0.113.1, 10.0.0.1")
                .param("src", "x")
                .param("utm_source", "twitter")
                .param("utm_medium", "social"))
        .andExpect(status().isNoContent());

    verify(recorder)
        .recordUsername(
            eq("visituser"),
            eq("https://t.co/x"),
            eq("Mozilla/5.0"),
            eq("203.0.113.1"),
            eq("ko-KR"),
            eq("x"),
            eq("twitter"),
            eq("social"),
            eq(null),
            eq(null),
            eq(null));
  }

  @Test
  void usesRemoteAddrWhenNoForwardedHeader() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@x.com", "google", "g-pvr"));
    user.claimUsername("remoteaddr");
    userRepository.save(user);

    mvc.perform(post("/api/v1/public/profiles/remoteaddr/visit")).andExpect(status().isNoContent());
  }
}
