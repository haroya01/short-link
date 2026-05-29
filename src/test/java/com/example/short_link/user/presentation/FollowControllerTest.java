package com.example.short_link.user.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import com.example.short_link.user.application.read.FollowQueryService;
import com.example.short_link.user.application.read.FollowStatus;
import com.example.short_link.user.application.write.FollowUseCase;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = FollowController.class)
@Import(UserExceptionHandler.class)
class FollowControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private FollowUseCase followUseCase;
  @MockitoBean private FollowQueryService followQueryService;

  private static final long USER_ID = 9L;

  @Test
  void statusReturnsFollowState() throws Exception {
    when(followQueryService.status(USER_ID, "bob")).thenReturn(new FollowStatus(true, 5L, 3L));

    mvc.perform(
            get("/api/v1/users/bob/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.following").value(true))
        .andExpect(jsonPath("$.followerCount").value(5))
        .andExpect(jsonPath("$.followingCount").value(3));
  }

  @Test
  void followReturnsUpdatedStatus() throws Exception {
    when(followUseCase.follow(USER_ID, "bob")).thenReturn(new FollowStatus(true, 1L, 0L));

    mvc.perform(
            put("/api/v1/users/bob/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.following").value(true));
  }

  @Test
  void unfollowReturnsUpdatedStatus() throws Exception {
    when(followUseCase.unfollow(USER_ID, "bob")).thenReturn(new FollowStatus(false, 0L, 0L));

    mvc.perform(
            delete("/api/v1/users/bob/follow")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.following").value(false));
  }

  @Test
  void followingYourselfIs400() throws Exception {
    when(followUseCase.follow(USER_ID, "me"))
        .thenThrow(new UserException(UserErrorCode.CANNOT_FOLLOW_SELF));

    mvc.perform(
            put("/api/v1/users/me/follow").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
  }

  @Test
  void anonymousFollowIs401() throws Exception {
    mvc.perform(put("/api/v1/users/bob/follow")).andExpect(status().isUnauthorized());
  }
}
