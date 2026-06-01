package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.LikedPostView;
import com.example.short_link.post.application.read.PostLikeQueryService;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = MyLikesController.class)
class MyLikesControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private PostLikeQueryService postLikeQueryService;

  private static final long USER_ID = 7L;

  @Test
  void myLikesReturnsList() throws Exception {
    when(postLikeQueryService.likedPosts(eq(USER_ID)))
        .thenReturn(List.of(new LikedPostView(42L, "alice", "My Post", "my-post")));

    mvc.perform(
            get("/api/v1/users/me/likes").header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(42))
        .andExpect(jsonPath("$[0].username").value("alice"))
        .andExpect(jsonPath("$[0].slug").value("my-post"));
  }

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/likes")).andExpect(status().isUnauthorized());
  }
}
