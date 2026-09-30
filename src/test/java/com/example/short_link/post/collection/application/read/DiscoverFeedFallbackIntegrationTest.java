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
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 공유 DB가 오염돼 있을 수 있어서 이 테스트가 만든 고유 제목으로만 단언한다.
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
    DiscoverableBodies.discoverable(p);
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
    Long loner = user("loner-dff", "ldff");
    Long stranger = user("stranger-dff", "sdff");
    connectPost(stranger, "DFF-global", post(stranger, "dff-uniq-global"));

    DiscoverFeedView feed = service.feed(loner, 0, 200, false);

    assertThat(feed.source()).isEqualTo("global");
    assertThat(feed.items()).anyMatch(i -> "Title dff-uniq-global".equals(i.title()));
  }

  @Test
  void quietFollowsFallBackOnFirstPageOnly() {
    Long viewer = user("viewer-dfq", "vdfq");
    Long quiet = user("quiet-dfq", "qdfq");
    followRepository.save(new FollowEntity(viewer, quiet));
    Long stranger = user("stranger-dfq", "sdfq");
    connectPost(stranger, "DFQ-global", post(stranger, "dfq-uniq-global"));

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

    DiscoverFeedView personalized = service.feed(viewer, 0, 200, false);
    assertThat(personalized.source()).isEqualTo("following");
    assertThat(personalized.items()).anyMatch(i -> "Title dfa-uniq-followed".equals(i.title()));
    assertThat(personalized.items()).noneMatch(i -> "Title dfa-uniq-global".equals(i.title()));

    DiscoverFeedView pinned = service.feed(viewer, 0, 200, true);
    assertThat(pinned.source()).isEqualTo("global");
    assertThat(pinned.items()).anyMatch(i -> "Title dfa-uniq-global".equals(i.title()));
    assertThat(pinned.items()).anyMatch(i -> "Title dfa-uniq-followed".equals(i.title()));
  }
}
