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

/**
 * 하이라이트 피드 콜드스타트 폴백 — 실제 MySQL 로 findRecentOnPublishedPosts 쿼리와 폴백 규칙을 검증한다. 전역 폴백에는 *발행된* 글의 구절만
 * 흐르고(초안 구절은 새지 않는다), 개인화가 차 있으면 그대로, 1페이지 이후의 빈 결과는 폴백하지 않는다. 조립은 글·큐레이터·작가·답글수를 일괄 해석하므로
 * query-audit 하네스로 N+1 이 없는지도 함께 관측한다. 공유 DB 오염 대비 이 테스트가 만든 고유 인용구로만 단언한다.
 */
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
    Long loner = user("loner-hff", "lhff"); // 아무도 팔로우하지 않는 콜드스타트 뷰어.
    Long stranger = user("stranger-hff", "shff");
    highlight(publishedPost(stranger, "hff-pub"), stranger, "hff-uniq-published");
    highlight(draftPost(stranger, "hff-draft"), stranger, "hff-uniq-draft");

    HighlightFeedView feed = service.feed(loner, 0, 200, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).anyMatch(i -> "hff-uniq-published".equals(i.quote()));
    // 초안 글의 구절은 전역 폴백으로도 새지 않는다.
    assertThat(feed.items()).noneMatch(i -> "hff-uniq-draft".equals(i.quote()));
  }

  @Test
  void quietFollowsFallBackOnFirstPageOnly() {
    Long viewer = user("viewer-hfq", "vhfq");
    Long quiet = user("quiet-hfq", "qhfq"); // 팔로우하지만 칠한 구절이 하나도 없다.
    followRepository.save(new FollowEntity(viewer, quiet));
    Long stranger = user("stranger-hfq", "shfq");
    highlight(publishedPost(stranger, "hfq-pub"), stranger, "hfq-uniq-global");

    // 첫 페이지: 개인화가 비어 전역으로 폴백.
    HighlightFeedView first = service.feed(viewer, 0, 200, false);
    assertThat(first.source()).isEqualTo("global");
    assertThat(first.items()).anyMatch(i -> "hfq-uniq-global".equals(i.quote()));

    // 1페이지 이후의 빈 결과는 정상 종료 — 폴백하지 않는다.
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

    // 팔로우 활동이 있으면 개인화 유지 — 팔로우 밖 구절은 섞이지 않는다.
    HighlightFeedView personalized = service.feed(viewer, 0, 200, false);
    assertThat(personalized.source()).isEqualTo("following");
    assertThat(personalized.items()).anyMatch(i -> "hfa-uniq-followed".equals(i.quote()));
    assertThat(personalized.items()).noneMatch(i -> "hfa-uniq-global".equals(i.quote()));

    // scope=global 고정 — 팔로우 활동이 있어도 전역 피드를 그대로 준다.
    HighlightFeedView pinned = service.feed(viewer, 0, 200, true);
    assertThat(pinned.source()).isEqualTo("global");
    assertThat(pinned.items()).anyMatch(i -> "hfa-uniq-global".equals(i.quote()));
    assertThat(pinned.items()).anyMatch(i -> "hfa-uniq-followed".equals(i.quote()));
  }
}
