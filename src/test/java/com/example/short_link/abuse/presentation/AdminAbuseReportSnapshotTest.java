package com.example.short_link.abuse.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
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
class AdminAbuseReportSnapshotTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository userRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private AbuseReportRepository abuseReportRepository;

  private String adminToken(String seed) {
    UserEntity admin = userRepository.save(new UserEntity(seed + "@x.com", "google", seed));
    admin.promoteToAdmin();
    return jwt.createAccessToken(userRepository.save(admin).getId(), "ADMIN");
  }

  private Long author(String seed, String handle) {
    UserEntity user = new UserEntity(seed + "@x.com", "google", seed);
    user.claimUsername(handle);
    return userRepository.save(user).getId();
  }

  /** Finds the report pointing at the given subject in the admin list response. */
  private JsonNode report(String token, long subjectId, String subjectType) throws Exception {
    String body =
        mvc.perform(get("/api/v1/admin/abuse-reports").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    for (JsonNode node : JSON.readTree(body)) {
      if (node.path("subjectId").asLong() == subjectId
          && subjectType.equals(node.path("subjectType").asText())) {
        return node;
      }
    }
    throw new AssertionError("report not found: " + subjectType + " #" + subjectId);
  }

  private static boolean nullish(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull();
  }

  @Test
  void plainUserCannotListReports() throws Exception {
    UserEntity user =
        userRepository.save(new UserEntity("abuse-user@x.com", "google", "g-abuse-user"));
    String token = jwt.createAccessToken(user.getId(), "USER");
    mvc.perform(get("/api/v1/admin/abuse-reports").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void reportOnPublishedPostCarriesSubjectSnapshot() throws Exception {
    Long authorId = author("g-snap-author", "snaphandle");
    PostEntity post = new PostEntity(authorId, "snap-slug", "신고당한 글", "ko");
    post.publish();
    postRepository.save(post);
    abuseReportRepository.save(
        new AbuseReportEntity(null, AbuseSubjectType.POST, post.getId(), "스팸"));

    JsonNode r = report(adminToken("g-snap-admin"), post.getId(), "POST");
    assertThat(r.get("subjectTitle").asText()).isEqualTo("신고당한 글");
    assertThat(r.get("subjectAuthorHandle").asText()).isEqualTo("snaphandle");
    assertThat(r.get("subjectUrl").asText()).endsWith("/p/snaphandle/snap-slug");
    assertThat(r.get("subjectRemoved").asBoolean()).isFalse();
  }

  @Test
  void reportOnUnpublishedPostIsMarkedRemoved() throws Exception {
    Long authorId = author("g-gone-author", "gonehandle");
    PostEntity post = new PostEntity(authorId, "gone-slug", "내려간 글", "ko");
    post.publish();
    post.unpublish();
    postRepository.save(post);
    abuseReportRepository.save(
        new AbuseReportEntity(null, AbuseSubjectType.POST, post.getId(), "규정 위반"));

    JsonNode r = report(adminToken("g-gone-admin"), post.getId(), "POST");
    assertThat(r.get("subjectTitle").asText()).isEqualTo("내려간 글");
    assertThat(r.get("subjectRemoved").asBoolean()).isTrue();
  }

  @Test
  void reportOnNonPostSubjectHasNoSnapshot() throws Exception {
    UserEntity target =
        userRepository.save(new UserEntity("target-u@x.com", "google", "g-target-u"));
    abuseReportRepository.save(
        new AbuseReportEntity(null, AbuseSubjectType.USER, target.getId(), "사칭"));

    JsonNode r = report(adminToken("g-user-admin"), target.getId(), "USER");
    assertThat(nullish(r, "subjectTitle")).isTrue();
    assertThat(nullish(r, "subjectAuthorHandle")).isTrue();
    assertThat(nullish(r, "subjectUrl")).isTrue();
    assertThat(r.get("subjectRemoved").asBoolean()).isFalse();
  }
}
