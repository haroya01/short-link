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
  }

  @Test
  void batch_emptyIds_returnsEmptyMap() {
    assertThat(service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of()))
        .isEmpty();
  }
}
