package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminPostTakedownTest {

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

  private PostEntity publishedPost(Long author, String slug) {
    PostEntity post = new PostEntity(author, slug, "제목", "ko");
    post.publish();
    return postRepository.save(post);
  }

  @Test
  void anonymousCannotTakeDownPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-atd-author"), "anon-takedown");
    mvc.perform(post("/api/v1/admin/posts/" + post.getId() + "/unpublish"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void plainUserCannotTakeDownPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-utd-author"), "user-takedown");
    UserEntity user = userRepository.save(new UserEntity("utd-user@x.com", "google", "g-utd-user"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(
            post("/api/v1/admin/posts/" + post.getId() + "/unpublish")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminTakesDownPublishedPost() throws Exception {
    PostEntity post = publishedPost(authorId("g-take-author"), "admin-takedown");
    mvc.perform(
            post("/api/v1/admin/posts/" + post.getId() + "/unpublish")
                .header("Authorization", "Bearer " + adminToken("g-take-admin")))
        .andExpect(status().isNoContent());
    assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
        .isEqualTo(PostStatus.UNPUBLISHED);
  }

  @Test
  void adminTakedownIsIdempotentWhenAlreadyUnpublished() throws Exception {
    PostEntity post = new PostEntity(authorId("g-idem-author"), "idem-takedown", "제목", "ko");
    post.publish();
    post.unpublish();
    postRepository.save(post);
    mvc.perform(
            post("/api/v1/admin/posts/" + post.getId() + "/unpublish")
                .header("Authorization", "Bearer " + adminToken("g-idem-admin")))
        .andExpect(status().isNoContent());
    assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
        .isEqualTo(PostStatus.UNPUBLISHED);
  }

  @Test
  void adminTakedownOfMissingPostIs404() throws Exception {
    mvc.perform(
            post("/api/v1/admin/posts/999999999/unpublish")
                .header("Authorization", "Bearer " + adminToken("g-miss-admin")))
        .andExpect(status().isNotFound());
  }
}
