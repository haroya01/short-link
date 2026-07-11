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

/**
 * "이 글들이 속한 길" 배치 — 실제 MySQL 로 벌크 쿼리(findAllByBlockTypeAndRefIdIn · findAllByIdIn ·
 * countByCollectionIdIn)를 검증한다. 여러 글을 한 번에 물어도 공개 컬렉션만 흐르고, 요청한 모든 글이 응답에 있으며(없으면 빈 올), count 는
 * 그룹-바이 한 쿼리로 맞는다. 공유 DB 오염 대비 이 테스트가 만든 고유 콘텐츠로만 단언한다.
 */
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
    Long p3 = post(alice, "batch-uniq-none"); // 아무 공개 컬렉션에도 없음.

    // 공개 컬렉션 pubA{p1,p2}, pubB{p1} — p1 은 두 공개 컬렉션에, p2 는 하나에.
    Long pubA = collection(alice, "batch-pubA", CollectionVisibility.PUBLIC);
    connect(pubA, p1, 0);
    connect(pubA, p2, 1);
    Long pubB = collection(alice, "batch-pubB", CollectionVisibility.PUBLIC);
    connect(pubB, p1, 0);
    // 비공개 컬렉션 — p2 를 담지만 절대 새지 않아야.
    Long priv = collection(alice, "batch-priv", CollectionVisibility.PRIVATE);
    connect(priv, p2, 0);

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(p1, p2, p3));

    // 요청한 세 글 모두 응답에 있다.
    assertThat(result).containsOnlyKeys(p1, p2, p3);

    // p1 은 공개 두 컬렉션(pubA·pubB) 모두, 비공개는 없음.
    assertThat(result.get(p1))
        .extracting(CollectionSummaryView::id)
        .containsExactlyInAnyOrder(pubA, pubB);
    assertThat(result.get(p1)).allSatisfy(v -> assertThat(v.visibility()).isEqualTo("PUBLIC"));

    // p2 는 공개 pubA 만(비공개 priv 는 빠진다).
    assertThat(result.get(p2)).extracting(CollectionSummaryView::id).containsExactly(pubA);

    // p3 은 어느 공개 컬렉션에도 없으니 빈 올.
    assertThat(result.get(p3)).isEmpty();

    // count 는 group-by 한 쿼리로 — pubA 는 두 글을 담았으니 2.
    CollectionSummaryView pubAView =
        result.get(p2).stream().filter(v -> v.id().equals(pubA)).findFirst().orElseThrow();
    assertThat(pubAView.count()).isEqualTo(2);

    // 큐레이터(소유자) 핸들이 실린다 — "카테고리"가 아니라 "@큐레이터의 길".
    assertThat(pubAView.curatorUsername()).isEqualTo("alice-batch");

    // 위치는 정렬된 연결 안 1-based 자리 — pubA{p1=0, p2=1} 이므로 p1=1번째, p2=2번째.
    Integer p1PosInPubA =
        result.get(p1).stream()
            .filter(v -> v.id().equals(pubA))
            .findFirst()
            .orElseThrow()
            .position();
    assertThat(p1PosInPubA).isEqualTo(1);
    assertThat(pubAView.position()).isEqualTo(2); // p2 는 pubA 에서 2번째

    // 같은 글 p1 이 다른 컬렉션 pubB 에서는 1번째(pubB 에 홀로).
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
    // raw position=5 이지만 정렬 순위는 2 — position 은 값이 아니라 순서다.
    assertThat(view.position()).isEqualTo(2);
    assertThat(view.count()).isEqualTo(2); // 분모
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
    assertThat(view.position()).isEqualTo(3); // "3편 중 3번째"
    assertThat(view.count()).isEqualTo(3);
  }

  @Test
  void batch_emptyIds_returnsEmptyMap() {
    assertThat(service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of()))
        .isEmpty();
  }
}
