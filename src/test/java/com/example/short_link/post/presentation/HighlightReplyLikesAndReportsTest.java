package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HighlightReplyLikesAndReportsTest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private PostRepository posts;
  @Autowired private PostHighlightRepository highlights;
  @Autowired private PostHighlightReplyRepository replies;
  @Autowired private JdbcTemplate jdbc;

  private long postId;
  private long highlightId;
  private long replyId;
  private String liker;
  private String outsider;
  private String admin;

  @BeforeEach
  void setUp() {
    long author = member("hrl-author");
    long reader = member("hrl-reader");
    long replier = member("hrl-replier");
    liker = token(member("hrl-liker"), "USER");
    outsider = token(member("hrl-outsider"), "USER");
    UserEntity moderator = users.save(new UserEntity("hrl-admin@x.com", "google", "g-hrl-admin"));
    moderator.promoteToAdmin();
    admin = token(users.save(moderator).getId(), "ADMIN");

    PostEntity post = new PostEntity(author, "hrl-post", "Replies worth liking", "ko");
    post.publish();
    postId = posts.save(post).getId();
    highlightId =
        highlights.save(new PostHighlightEntity(postId, reader, 0, 0, 0, 5, "quote", null)).getId();
    replyId =
        replies
            .save(new PostHighlightReplyEntity(highlightId, replier, "a reply under the quote"))
            .getId();
  }

  @Test
  void aLikeCountsForEveryoneButReadsAsLikedOnlyForTheLiker() throws Exception {
    like(liker).andExpect(status().isOk()).andExpect(jsonPath("$.likeCount").value(1));
    like(liker)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.liked").value(true));

    thread(liker)
        .andExpect(jsonPath("$[0].likeCount").value(1))
        .andExpect(jsonPath("$[0].liked").value(true));
    thread(outsider)
        .andExpect(jsonPath("$[0].likeCount").value(1))
        .andExpect(jsonPath("$[0].liked").value(false));
    thread(null)
        .andExpect(jsonPath("$[0].likeCount").value(1))
        .andExpect(jsonPath("$[0].liked").value(false));

    mvc.perform(delete(likePath()).header("Authorization", "Bearer " + liker))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.liked").value(false));
    thread(liker)
        .andExpect(jsonPath("$[0].likeCount").value(0))
        .andExpect(jsonPath("$[0].liked").value(false));
  }

  @Test
  void aReportedReplyReachesTheQueueAndATakeDownHidesItFromEveryRead() throws Exception {
    mvc.perform(
            post("/api/v1/public/abuse-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subjectType\":\"HIGHLIGHT_REPLY\",\"subjectId\":"
                        + replyId
                        + ",\"reasonCode\":\"SPAM\"}"))
        .andExpect(status().isAccepted());
    long reportId =
        jdbc.queryForObject(
            "SELECT id FROM abuse_report WHERE subject_type = 'HIGHLIGHT_REPLY' AND subject_id = ?",
            Long.class,
            replyId);
    queued(reportId, false);
    highlightsOnPost().andExpect(jsonPath("$[0].replyCount").value(1));

    mvc.perform(
            post("/api/v1/admin/abuse-reports/" + reportId + "/resolve")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"RESOLVED\",\"action\":\"DELETE_HIGHLIGHT_REPLY\"}"))
        .andExpect(status().isOk());

    queued(reportId, true);
    thread(null).andExpect(jsonPath("$.length()").value(0));
    thread(liker).andExpect(jsonPath("$.length()").value(0));
    highlightsOnPost().andExpect(jsonPath("$[0].replyCount").value(0));
    like(liker)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("HIGHLIGHT_REPLY_NOT_FOUND"));
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM highlight_reply WHERE id = ?",
                Boolean.class,
                replyId))
        .isTrue();
  }

  @Test
  void aCommentTakeDownCannotBeAimedAtAHighlightReply() throws Exception {
    mvc.perform(
            post("/api/v1/public/abuse-reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"subjectType\":\"HIGHLIGHT_REPLY\",\"subjectId\":"
                        + replyId
                        + ",\"reasonCode\":\"SPAM\"}"))
        .andExpect(status().isAccepted());
    long reportId =
        jdbc.queryForObject(
            "SELECT id FROM abuse_report WHERE subject_type = 'HIGHLIGHT_REPLY' AND subject_id = ?",
            Long.class,
            replyId);

    mvc.perform(
            post("/api/v1/admin/abuse-reports/" + reportId + "/resolve")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"RESOLVED\",\"action\":\"DELETE_COMMENT\"}"))
        .andExpect(status().isBadRequest());
    thread(null).andExpect(jsonPath("$[0].id").value(replyId));
  }

  private ResultActions like(String token) throws Exception {
    return mvc.perform(post(likePath()).header("Authorization", "Bearer " + token));
  }

  private ResultActions thread(String token) throws Exception {
    var request = get("/api/v1/public/highlights/" + highlightId + "/replies");
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return mvc.perform(request).andExpect(status().isOk());
  }

  private ResultActions highlightsOnPost() throws Exception {
    return mvc.perform(get("/api/v1/public/posts/" + postId + "/highlights"))
        .andExpect(status().isOk());
  }

  private void queued(long reportId, boolean removed) throws Exception {
    mvc.perform(get("/api/v1/admin/abuse-reports").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + reportId + ")].subjectType").value("HIGHLIGHT_REPLY"))
        .andExpect(
            jsonPath("$[?(@.id == " + reportId + ")].subjectExcerpt")
                .value("a reply under the quote"))
        .andExpect(
            jsonPath("$[?(@.id == " + reportId + ")].subjectAuthorHandle").value("hrl-replier"))
        .andExpect(jsonPath("$[?(@.id == " + reportId + ")].subjectRemoved").value(removed));
  }

  private String likePath() {
    return "/api/v1/highlight-replies/" + replyId + "/like";
  }

  private long member(String handle) {
    UserEntity user = new UserEntity(handle + "@x.com", "google", "g-" + handle);
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private String token(long userId, String role) {
    return jwt.createAccessToken(userId, role);
  }
}
