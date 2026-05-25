package com.example.short_link.tag.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.tag.application.LinkTagService;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
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
class LinkTagControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;

  @MockitoBean private LinkTagService service;

  @Test
  void anonymousGetIs401() throws Exception {
    mvc.perform(get("/api/v1/links/abc1234/tags")).andExpect(status().isUnauthorized());
  }

  @Test
  void getReturnsTagNames() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("t@x.com", "google", "g-lt"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.tagNamesFor(eq(user.getId()), eq("abc1234")))
        .thenReturn(List.of("work", "urgent"));

    mvc.perform(get("/api/v1/links/abc1234/tags").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shortCode").value("abc1234"))
        .andExpect(jsonPath("$.tags[0]").value("work"));
  }

  @Test
  void replaceTagsReturnsNewList() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("r@x.com", "google", "g-ltr"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    when(service.replaceTags(eq(user.getId()), eq("abc1234"), any()))
        .thenReturn(List.of("new-tag"));

    mvc.perform(
            put("/api/v1/links/abc1234/tags")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\":[\"new-tag\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tags[0]").value("new-tag"));
  }
}
