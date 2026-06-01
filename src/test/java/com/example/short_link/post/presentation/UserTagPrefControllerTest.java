package com.example.short_link.post.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.application.read.TagPrefQueryService;
import com.example.short_link.post.application.read.TagPrefsView;
import com.example.short_link.post.application.write.SetTagPrefUseCase;
import com.example.short_link.testsupport.KurlWebMvcTest;
import com.example.short_link.testsupport.WebMvcSecurityTestConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(controllers = UserTagPrefController.class)
class UserTagPrefControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SetTagPrefUseCase setTagPref;
  @MockitoBean private TagPrefQueryService tagPrefQueryService;

  private static final long USER_ID = 7L;

  @Test
  void getReturnsPrefs() throws Exception {
    when(tagPrefQueryService.get(eq(USER_ID)))
        .thenReturn(new TagPrefsView(List.of("개발"), List.of("광고")));

    mvc.perform(
            get("/api/v1/users/me/tag-prefs")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followed[0]").value("개발"))
        .andExpect(jsonPath("$.hidden[0]").value("광고"));
  }

  @Test
  void followReturnsUpdatedPrefs() throws Exception {
    when(tagPrefQueryService.get(eq(USER_ID)))
        .thenReturn(new TagPrefsView(List.of("개발"), List.of()));

    mvc.perform(
            put("/api/v1/users/me/tag-prefs/followed/개발")
                .header(WebMvcSecurityTestConfig.USER_ID_HEADER, USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followed[0]").value("개발"));
  }

  @Test
  void anonymousIs401() throws Exception {
    mvc.perform(get("/api/v1/users/me/tag-prefs")).andExpect(status().isUnauthorized());
  }
}
