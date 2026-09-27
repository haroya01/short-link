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
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// 공유 DB가 오염돼 있을 수 있어서 이 테스트가 만든 고유 콘텐츠로만 단언한다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicPostCollectionsBatchIntegrationTest {

  @Autowired private CollectionQueryService service;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;

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

  private Long collection(Long ownerId, String title, CollectionVisibility vis) {
    return collectionRepository
        .save(new CollectionEntity(ownerId, title, null, vis, CollectionKind.COLLECTION))
        .getId();
  }

  private void connect(Long collectionId, Long postId, int pos) {
    connectionRepository.save(
        new CollectionConnectionEntity(collectionId, ConnectionBlockType.POST, postId, null, pos));
  }

  @Test
  void batch_groupsPublicCollectionsPerPost_excludingPrivate_fillingMissingWithEmpty() {
    Long alice = user("alice-batch", "abt");

    Long p1 = post(alice, "batch-uniq-one");
    Long p2 = post(alice, "batch-uniq-two");
    Long p3 = post(alice, "batch-uniq-none");

    Long pubA = collection(alice, "batch-pubA", CollectionVisibility.PUBLIC);
    connect(pubA, p1, 0);
    connect(pubA, p2, 1);
    Long pubB = collection(alice, "batch-pubB", CollectionVisibility.PUBLIC);
    connect(pubB, p1, 0);
    Long priv = collection(alice, "batch-priv", CollectionVisibility.PRIVATE);
    connect(priv, p2, 0);

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(p1, p2, p3));

    assertThat(result).containsOnlyKeys(p1, p2, p3);

    assertThat(result.get(p1))
        .extracting(CollectionSummaryView::id)
        .containsExactlyInAnyOrder(pubA, pubB);
    assertThat(result.get(p1)).allSatisfy(v -> assertThat(v.visibility()).isEqualTo("PUBLIC"));

    assertThat(result.get(p2)).extracting(CollectionSummaryView::id).containsExactly(pubA);

    assertThat(result.get(p3)).isEmpty();

    CollectionSummaryView pubAView =
        result.get(p2).stream().filter(v -> v.id().equals(pubA)).findFirst().orElseThrow();
    assertThat(pubAView.count()).isEqualTo(2);

    assertThat(pubAView.curatorUsername()).isEqualTo("alice-batch");

    Integer p1PosInPubA =
        result.get(p1).stream()
            .filter(v -> v.id().equals(pubA))
            .findFirst()
            .orElseThrow()
            .position();
    assertThat(p1PosInPubA).isEqualTo(1);
    assertThat(pubAView.position()).isEqualTo(2);

    Integer p1PosInPubB =
        result.get(p1).stream()
            .filter(v -> v.id().equals(pubB))
            .findFirst()
            .orElseThrow()
            .position();
    assertThat(p1PosInPubB).isEqualTo(1);
  }

  @Test
  void batch_positionCountsByOrder_notByRawSparsePositionValue() {
    Long bob = user("bob-sparse", "bsp");
    Long first = post(bob, "sparse-first");
    Long target = post(bob, "sparse-target");

    // 삭제·재배치로 raw position 이 듬성해진 상황(0, 그리고 5) — 값 5 를 그대로 쓰면 안 되고 순서로 2번째여야 한다.
    Long col = collection(bob, "sparse-col", CollectionVisibility.PUBLIC);
    connect(col, first, 0);
    connect(col, target, 5);

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(target));

    CollectionSummaryView view =
        result.get(target).stream().filter(v -> v.id().equals(col)).findFirst().orElseThrow();
    assertThat(view.position()).isEqualTo(2);
    assertThat(view.count()).isEqualTo(2);
  }

  @Test
  void single_carriesCuratorAndPosition() {
    Long carol = user("carol-single", "csg");
    Long a = post(carol, "single-a");
    Long b = post(carol, "single-b");
    Long target = post(carol, "single-target");

    Long col = collection(carol, "single-col", CollectionVisibility.PUBLIC);
    connect(col, a, 0);
    connect(col, b, 1);
    connect(col, target, 2);

    List<CollectionSummaryView> views =
        service.publicCollectionsContaining(ConnectionBlockType.POST, target);

    CollectionSummaryView view =
        views.stream().filter(v -> v.id().equals(col)).findFirst().orElseThrow();
    assertThat(view.curatorUsername()).isEqualTo("carol-single");
    assertThat(view.position()).isEqualTo(3);
    assertThat(view.count()).isEqualTo(3);
  }

  @Test
  void batch_emptyIds_returnsEmptyMap() {
    assertThat(service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of()))
        .isEmpty();
  }
}
