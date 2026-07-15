package com.example.short_link.user.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import com.example.short_link.user.application.read.FollowListQueryService;
import com.example.short_link.user.application.read.FollowListView;
import com.example.short_link.user.application.read.FollowUserView;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = FollowListController.class)
@Import(UserExceptionHandler.class)
class FollowListControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private FollowListQueryService followListQueryService;

  private static final long USER_ID = 9L;

  private FollowListView page() {
    return new FollowListView(
        List.of(new FollowUserView(10L, "alice", "bio", null, 7L, true)), 0, 20, false);
  }

  @Test
  void followersReturnsRows() throws Exception {
    when(followListQueryService.followers(USER_ID, "bob", 0, 20)).thenReturn(page());

    mvc.perform(
            get("/api/v1/users/bob/followers")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].username").value("alice"))
        .andExpect(jsonPath("$.items[0].followedByMe").value(true))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void followingReturnsRows() throws Exception {
    when(followListQueryService.following(USER_ID, "bob", 0, 20)).thenReturn(page());

    mvc.perform(
            get("/api/v1/users/bob/following")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].followerCount").value(7));
  }

  @Test
  void hiddenListsSurfaceAs403() throws Exception {
    when(followListQueryService.followers(USER_ID, "bob", 0, 20))
        .thenThrow(new UserException(UserErrorCode.FOLLOW_LIST_HIDDEN));

    mvc.perform(
            get("/api/v1/users/bob/followers")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isForbidden());
  }

  @Test
  void clampsPageAndSize() throws Exception {
    when(followListQueryService.followers(eq(USER_ID), eq("bob"), eq(0), eq(50)))
        .thenReturn(page());

    mvc.perform(
            get("/api/v1/users/bob/followers")
                .param("page", "-3")
                .param("size", "999")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk());

    verify(followListQueryService).followers(USER_ID, "bob", 0, 50);
  }
}
