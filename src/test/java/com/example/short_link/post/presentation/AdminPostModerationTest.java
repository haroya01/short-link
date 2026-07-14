package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
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

/**
 * Admin moderation of any author's post — metadata edit (title/tags) and permanent delete. Mirrors
 * {@link AdminPostTakedownTest}: the {@code /api/v1/admin/**} prefix is ADMIN-gated at the security
 * layer, so the tests pin the role gate plus the use-case behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPostModerationTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private PostRepository postRepository;

  private Long authorId(String seed) {
    return userRepository.save(new UserEntity(seed + "@x.com", "google", seed)).getId();
  }

  private String adminToken(String seed) {
    UserEntity admin = userRepository.save(new UserEntity(seed + "@x.com", "google", seed));
    admin.promoteToAdmin();
    return jwt.createAccessToken(userRepository.save(admin).getId(), "ADMIN");
  }

  private String userToken(String seed) {
    UserEntity user = userRepository.save(new UserEntity(seed + "@x.com", "google", seed));
    return jwt.createAccessToken(userRepository.save(user).getId(), "USER");
  }

  private PostEntity publishedPost(Long author, String slug) {
    PostEntity post = new PostEntity(author, slug, "제목", "ko");
    post.publish();
    return postRepository.save(post);
  }

  // MARK: 편집 (제목·태그)

  @Test
  void plainUserCannotEditOthersPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-ae-author1"), "admin-edit-gate");
    mvc.perform(
            patch("/api/v1/admin/posts/" + post.getId())
                .header("Authorization", "Bearer " + userToken("g-ae-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"바꿈\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminEditsTitleAndTagsOfOthersPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-ae-author2"), "admin-edit");
    mvc.perform(
            patch("/api/v1/admin/posts/" + post.getId())
                .header("Authorization", "Bearer " + adminToken("g-ae-admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"정책 위반 표현 정리\",\"tags\":[\"공지\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("정책 위반 표현 정리"))
        .andExpect(jsonPath("$.tags[0]").value("공지"));
    assertThat(postRepository.findById(post.getId()).orElseThrow().getTitle())
        .isEqualTo("정책 위반 표현 정리");
  }

  @Test
  void adminEditWithNullFieldsLeavesPostUnchanged() throws Exception {
    PostEntity post = publishedPost(authorId("g-ae-author3"), "admin-edit-null");
    mvc.perform(
            patch("/api/v1/admin/posts/" + post.getId())
                .header("Authorization", "Bearer " + adminToken("g-ae-admin2"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("제목"));
  }

  @Test
  void adminEditOfMissingPostIs404() throws Exception {
    mvc.perform(
            patch("/api/v1/admin/posts/999999999")
                .header("Authorization", "Bearer " + adminToken("g-ae-miss"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isNotFound());
  }

  // MARK: 삭제

  @Test
  void plainUserCannotDeleteOthersPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-ad-author1"), "admin-del-gate");
    mvc.perform(
            delete("/api/v1/admin/posts/" + post.getId())
                .header("Authorization", "Bearer " + userToken("g-ad-user")))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminDeletesOthersPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-ad-author2"), "admin-del");
    mvc.perform(
            delete("/api/v1/admin/posts/" + post.getId())
                .header("Authorization", "Bearer " + adminToken("g-ad-admin")))
        .andExpect(status().isNoContent());
    assertThat(postRepository.findById(post.getId())).isEmpty();
  }

  @Test
  void adminDeleteOfMissingPostIs404() throws Exception {
    mvc.perform(
            delete("/api/v1/admin/posts/999999999")
                .header("Authorization", "Bearer " + adminToken("g-ad-miss")))
        .andExpect(status().isNotFound());
  }
}
