package com.example.short_link.post.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import io.queryaudit.junit5.QueryAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 공유 DB가 오염돼 있을 수 있어서 이 테스트가 만든 고유 인용구로만 단언한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@QueryAudit
class HighlightFeedFallbackIntegrationTest {

  @Autowired private PostHighlightQueryService service;
  @Autowired private PostHighlightRepository highlightRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowRepository followRepository;

  private Long user(String username, String seed) {
    UserEntity u = new UserEntity(seed + "@x.com", "google", "g-" + seed);
    u.claimUsername(username);
    return userRepository.save(u).getId();
  }

  private Long publishedPost(Long authorId, String slug) {
    PostEntity p = new PostEntity(authorId, slug, "Title " + slug, "ko");
    p.publish();
    return postRepository.save(p).getId();
  }

  private Long draftPost(Long authorId, String slug) {
    return postRepository.save(new PostEntity(authorId, slug, "Title " + slug, "ko")).getId();
  }

  private void highlight(Long postId, Long userId, String quote) {
    highlightRepository.save(new PostHighlightEntity(postId, userId, 0, 0, 0, 3, quote, null));
  }

  @Test
  void followerlessViewerGetsGlobalFeedOfPublishedPostsOnly() {
    Long loner = user("loner-hff", "lhff");
    Long stranger = user("stranger-hff", "shff");
    highlight(publishedPost(stranger, "hff-pub"), stranger, "hff-uniq-published");
    highlight(draftPost(stranger, "hff-draft"), stranger, "hff-uniq-draft");

    HighlightFeedView feed = service.feed(loner, 0, 200, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).anyMatch(i -> "hff-uniq-published".equals(i.quote()));
    assertThat(feed.items()).noneMatch(i -> "hff-uniq-draft".equals(i.quote()));
  }

  @Test
  void quietFollowsFallBackOnFirstPageOnly() {
    Long viewer = user("viewer-hfq", "vhfq");
    Long quiet = user("quiet-hfq", "qhfq");
    followRepository.save(new FollowEntity(viewer, quiet));
    Long stranger = user("stranger-hfq", "shfq");
    highlight(publishedPost(stranger, "hfq-pub"), stranger, "hfq-uniq-global");

    HighlightFeedView first = service.feed(viewer, 0, 200, false);
    assertThat(first.source()).isEqualTo("global");
    assertThat(first.items()).anyMatch(i -> "hfq-uniq-global".equals(i.quote()));

    HighlightFeedView second = service.feed(viewer, 1, 200, false);
    assertThat(second.source()).isEqualTo("following");
    assertThat(second.items()).isEmpty();
  }

  @Test
  void activeFollowsStayPersonalizedAndScopeGlobalPins() {
    Long viewer = user("viewer-hfa", "vhfa");
    Long followed = user("followed-hfa", "fhfa");
    followRepository.save(new FollowEntity(viewer, followed));
    highlight(publishedPost(followed, "hfa-followed"), followed, "hfa-uniq-followed");
    Long stranger = user("stranger-hfa", "shfa");
    highlight(publishedPost(stranger, "hfa-global"), stranger, "hfa-uniq-global");

    HighlightFeedView personalized = service.feed(viewer, 0, 200, false);
    assertThat(personalized.source()).isEqualTo("following");
    assertThat(personalized.items()).anyMatch(i -> "hfa-uniq-followed".equals(i.quote()));
    assertThat(personalized.items()).noneMatch(i -> "hfa-uniq-global".equals(i.quote()));

    HighlightFeedView pinned = service.feed(viewer, 0, 200, true);
    assertThat(pinned.source()).isEqualTo("global");
    assertThat(pinned.items()).anyMatch(i -> "hfa-uniq-global".equals(i.quote()));
    assertThat(pinned.items()).anyMatch(i -> "hfa-uniq-followed".equals(i.quote()));
  }
}
