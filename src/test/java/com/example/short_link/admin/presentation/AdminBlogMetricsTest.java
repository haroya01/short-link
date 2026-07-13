package com.example.short_link.admin.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.abuse.domain.AbuseReason;
import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hamcrest.Matchers;
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
class AdminBlogMetricsTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private AbuseReportRepository abuseReportRepository;
  @Autowired private EntityManager em;

  @Test
  void plainUserCannotReadBlogMetrics() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("bm-user@x.com", "google", "g-bm-user"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/blog/metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminReadsBlogMetricsInExpectedShape() throws Exception {
    UserEntity author = new UserEntity("bm-author@x.com", "google", "g-bm-author");
    author.claimUsername("topauthor");
    author = userRepository.save(author);

    PostEntity post = new PostEntity(author.getId(), "top-slug", "최다 조회 글", "ko");
    post.publish();
    post = postRepository.save(post);
    // A view count far above any residual test data so this post is deterministically the #1 read.
    em.createNativeQuery("UPDATE posts SET view_count = :v WHERE id = :id")
        .setParameter("v", 1_000_000_000L)
        .setParameter("id", post.getId())
        .executeUpdate();
    abuseReportRepository.save(
        new AbuseReportEntity(null, AbuseSubjectType.POST, post.getId(), AbuseReason.OTHER, "테스트"));

    UserEntity admin =
        userRepository.save(new UserEntity("bm-admin@x.com", "google", "g-bm-admin"));
    admin.promoteToAdmin();
    String token = jwt.createAccessToken(userRepository.save(admin).getId(), "ADMIN");

    mvc.perform(get("/api/v1/admin/blog/metrics").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPosts").isNumber())
        .andExpect(jsonPath("$.totalReads").isNumber())
        .andExpect(jsonPath("$.activeAuthors").isNumber())
        .andExpect(jsonPath("$.openReports").isNumber())
        .andExpect(jsonPath("$.topPosts").isArray())
        .andExpect(jsonPath("$.topPosts[0].id").value(post.getId().intValue()))
        .andExpect(jsonPath("$.topPosts[0].title").value("최다 조회 글"))
        .andExpect(jsonPath("$.topPosts[0].authorHandle").value("topauthor"))
        .andExpect(jsonPath("$.topPosts[0].reads").value(1000000000))
        .andExpect(jsonPath("$.topPosts[0].url").value(Matchers.endsWith("/p/topauthor/top-slug")));
  }
}
