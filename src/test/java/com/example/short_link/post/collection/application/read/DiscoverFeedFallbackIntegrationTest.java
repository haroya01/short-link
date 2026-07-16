package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionKind;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.collection.domain.repository.CollectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발견 피드 콜드스타트 폴백 — 실제 MySQL 로 검증한다. 팔로우가 없거나 팔로우들의 공개 연결이 없어 첫 페이지가 비면 전역 공개 피드로
 * 폴백하고(source=global), 개인화가 차 있으면 그대로(source=following), 1페이지 이후의 빈 결과는 폴백하지 않는다. 공유 DB 오염 대비 이
 * 테스트가 만든 고유 제목으로만 단언한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DiscoverFeedFallbackIntegrationTest {

  @Autowired private DiscoverFeedQueryService service;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowRepository followRepository;

  private Long user(String username, String seed) {
    UserEntity u = new UserEntity(seed + "@x.com", "google", "g-" + seed);
    u.claimUsername(username);
    return userRepository.save(u).getId();
  }

  private Long post(Long authorId, String slug) {
    PostEntity p = new PostEntity(authorId, slug, "Title " + slug, "ko");
    p.publish();
    return postRepository.save(p).getId();
  }

  private void connectPost(Long ownerId, String collectionTitle, Long postId) {
    Long collectionId =
        collectionRepository
            .save(
                new CollectionEntity(
                    ownerId,
                    collectionTitle,
                    null,
                    CollectionVisibility.PUBLIC,
                    CollectionKind.COLLECTION))
            .getId();
    connectionRepository.save(
        new CollectionConnectionEntity(collectionId, ConnectionBlockType.POST, postId, null, 0));
  }

  @Test
  void followerlessViewerFallsBackToGlobal() {
    Long loner = user("loner-dff", "ldff"); // 아무도 팔로우하지 않는 콜드스타트 뷰어.
    Long stranger = user("stranger-dff", "sdff");
    connectPost(stranger, "DFF-global", post(stranger, "dff-uniq-global"));

    DiscoverFeedView feed = service.feed(loner, 0, 200, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).anyMatch(i -> "Title dff-uniq-global".equals(i.title()));
  }

  @Test
  void quietFollowsFallBackOnFirstPageOnly() {
    Long viewer = user("viewer-dfq", "vdfq");
    Long quiet = user("quiet-dfq", "qdfq"); // 팔로우하지만 공개 연결이 하나도 없다.
    followRepository.save(new FollowEntity(viewer, quiet));
    Long stranger = user("stranger-dfq", "sdfq");
    connectPost(stranger, "DFQ-global", post(stranger, "dfq-uniq-global"));

    // 첫 페이지: 개인화가 비어 전역으로 폴백 — 팔로우 밖 큐레이터의 연결이 흐른다.
    DiscoverFeedView first = service.feed(viewer, 0, 200, false);
    assertThat(first.source()).isEqualTo("global");
    assertThat(first.items()).anyMatch(i -> "Title dfq-uniq-global".equals(i.title()));

    // 1페이지 이후의 빈 결과는 정상 종료 — 전역을 섞으면 페이지가 오염되므로 폴백하지 않는다.
    DiscoverFeedView second = service.feed(viewer, 1, 200, false);
    assertThat(second.source()).isEqualTo("following");
    assertThat(second.items()).isEmpty();
  }

  @Test
  void activeFollowsStayPersonalizedAndScopeGlobalPins() {
    Long viewer = user("viewer-dfa", "vdfa");
    Long followed = user("followed-dfa", "fdfa");
    followRepository.save(new FollowEntity(viewer, followed));
    connectPost(followed, "DFA-followed", post(followed, "dfa-uniq-followed"));
    Long stranger = user("stranger-dfa", "sdfa");
    connectPost(stranger, "DFA-global", post(stranger, "dfa-uniq-global"));

    // 팔로우 활동이 있으면 개인화 유지 — 팔로우 밖 연결은 섞이지 않는다.
    DiscoverFeedView personalized = service.feed(viewer, 0, 200, false);
    assertThat(personalized.source()).isEqualTo("following");
    assertThat(personalized.items()).anyMatch(i -> "Title dfa-uniq-followed".equals(i.title()));
    assertThat(personalized.items()).noneMatch(i -> "Title dfa-uniq-global".equals(i.title()));

    // scope=global 고정 — 팔로우 활동이 있어도 전역 피드를 그대로 준다.
    DiscoverFeedView pinned = service.feed(viewer, 0, 200, true);
    assertThat(pinned.source()).isEqualTo("global");
    assertThat(pinned.items()).anyMatch(i -> "Title dfa-uniq-global".equals(i.title()));
    assertThat(pinned.items()).anyMatch(i -> "Title dfa-uniq-followed".equals(i.title()));
  }
}
