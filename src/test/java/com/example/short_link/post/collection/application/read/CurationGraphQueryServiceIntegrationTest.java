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
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 큐레이션 그래프 — 실제 MySQL 로 self-join 네이티브 쿼리(공동 등장 · 큐레이터 겹침)를 검증한다. 사람이 손으로 엮은 *공개* 컬렉션 연결만 그래프 간선이
 * 되고, PRIVATE 은 빠지며 자기 자신은 제외된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurationGraphQueryServiceIntegrationTest {

  @Autowired private CurationGraphQueryService service;
  @Autowired private CollectionRepository collectionRepository;
  @Autowired private CollectionConnectionRepository connectionRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private PostHighlightRepository highlightRepository;
  @Autowired private NoteRepository noteRepository;
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

  private Long highlight(Long postId, Long userId, String quote) {
    return highlightRepository
        .save(new PostHighlightEntity(postId, userId, 0, 0, 0, 3, quote, null))
        .getId();
  }

  private Long note(Long userId, String body) {
    return noteRepository.save(new NoteEntity(userId, body)).getId();
  }

  private Long collection(Long ownerId, String title, CollectionVisibility vis) {
    return collectionRepository
        .save(new CollectionEntity(ownerId, title, null, vis, CollectionKind.COLLECTION))
        .getId();
  }

  private void connect(Long collectionId, ConnectionBlockType type, Long refId, int pos) {
    connectionRepository.save(new CollectionConnectionEntity(collectionId, type, refId, null, pos));
  }

  @Test
  void relatedTo_returnsCoOccurringBlocks_weightedByPublicCollections_excludingSelfAndPrivate() {
    Long alice = user("alice-graph", "agr");
    Long bob = user("bob-graph", "bgr");

    Long postP = post(alice, "p-seed");
    Long h1 = highlight(postP, alice, "좋은 추상은 더 지울 게 없을 때");
    Long p1 = post(alice, "p-one");
    Long p2 = post(alice, "p-two");
    Long p3 = post(bob, "p-three");
    Long pSecret = post(alice, "p-secret");
    Long n1 = note(alice, "더 나은 질문을 기다리는 일");

    // 공개: C1{h1, p1, n1}, C2{h1, p1, p2} — p1 은 두 공개 컬렉션 모두에서 h1 과 공존(가중치 2).
    Long c1 = collection(alice, "C1", CollectionVisibility.PUBLIC);
    connect(c1, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(c1, ConnectionBlockType.POST, p1, 1);
    connect(c1, ConnectionBlockType.NOTE, n1, 2);
    Long c2 = collection(alice, "C2", CollectionVisibility.PUBLIC);
    connect(c2, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(c2, ConnectionBlockType.POST, p1, 1);
    connect(c2, ConnectionBlockType.POST, p2, 2);
    // 다른 큐레이터의 공개: C3{h1, p3}.
    Long c3 = collection(bob, "C3", CollectionVisibility.PUBLIC);
    connect(c3, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(c3, ConnectionBlockType.POST, p3, 1);
    // 비공개: C4{h1, pSecret} — 그래프에서 절대 새지 않아야.
    Long c4 = collection(alice, "C4", CollectionVisibility.PRIVATE);
    connect(c4, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(c4, ConnectionBlockType.POST, pSecret, 1);

    List<RelatedBlockView> related = service.relatedTo(ConnectionBlockType.HIGHLIGHT, h1, 24);

    // id 는 테이블별로 매겨져 글·하이라이트·노트가 같은 숫자일 수 있다(신선한 CI DB). 또 공유 DB 라 다른
    // 테스트 커밋 행이 섞일 수 있다. 그래서 (blockType, refId) *쌍* 으로 키를 잡고 내 블록만 추려 본다.
    java.util.Set<String> mine =
        java.util.Set.of(
            "POST:" + p1,
            "POST:" + p2,
            "POST:" + p3,
            "NOTE:" + n1,
            "HIGHLIGHT:" + h1,
            "POST:" + pSecret);
    List<String> minePresent =
        related.stream()
            .map(r -> r.blockType() + ":" + r.refId())
            .filter(mine::contains)
            .distinct()
            .toList();
    // 씨앗(HIGHLIGHT:h1)·비공개(POST:pSecret)는 빠지고 공개 공존 블록 4개만.
    assertThat(minePresent)
        .containsExactlyInAnyOrder("POST:" + p1, "POST:" + p2, "POST:" + p3, "NOTE:" + n1);
    // p1 은 두 공개 컬렉션에서 h1 과 공존 → sharedCount 2(공동 등장 가중치). NOTE n1 은 1.
    RelatedBlockView p1View =
        related.stream()
            .filter(r -> r.blockType().equals("POST") && r.refId().equals(p1))
            .findFirst()
            .orElseThrow();
    assertThat(p1View.sharedCount()).isEqualTo(2);
    RelatedBlockView n1View =
        related.stream()
            .filter(r -> r.blockType().equals("NOTE") && r.refId().equals(n1))
            .findFirst()
            .orElseThrow();
    assertThat(n1View.sharedCount()).isEqualTo(1);
  }

  @Test
  void kindredCurators_findsOverlappingCurators_excludingSelfAndPrivate() {
    Long alice = user("alice-kin", "akn");
    Long bob = user("bob-kin", "bkn");

    Long postP = post(alice, "k-seed");
    Long h1 = highlight(postP, alice, "겹치는 것은 취향");
    Long p1 = post(alice, "k-one");
    Long pSecret = post(alice, "k-secret");

    // alice 공개: {h1, p1}. bob 공개: {h1} → h1 한 항목 겹침.
    Long ac = collection(alice, "A-pub", CollectionVisibility.PUBLIC);
    connect(ac, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(ac, ConnectionBlockType.POST, p1, 1);
    Long bc = collection(bob, "B-pub", CollectionVisibility.PUBLIC);
    connect(bc, ConnectionBlockType.HIGHLIGHT, h1, 0);
    // bob 비공개로 p1 도 엮음 → 비공개는 겹침으로 세지 않아야.
    Long bcPriv = collection(bob, "B-priv", CollectionVisibility.PRIVATE);
    connect(bcPriv, ConnectionBlockType.POST, p1, 0);
    // alice 비공개 pSecret — 자기 비공개도 그래프 밖.
    Long acPriv = collection(alice, "A-priv", CollectionVisibility.PRIVATE);
    connect(acPriv, ConnectionBlockType.POST, pSecret, 0);

    List<KindredCuratorView> kindred = service.kindredCurators("alice-kin", 12);

    // bob 은 들고 자기(alice)는 빠진다. 공개 겹침은 h1 하나 → bob 의 sharedItems 1.
    // 공유 DB 오염 대비 hasSize 대신 contains/자기제외만 단언(bob·alice 는 이 테스트 고유 유저).
    assertThat(kindred)
        .extracting(k -> k.curator().username())
        .contains("bob-kin")
        .doesNotContain("alice-kin");
    KindredCuratorView bobView =
        kindred.stream()
            .filter(k -> k.curator().username().equals("bob-kin"))
            .findFirst()
            .orElseThrow();
    assertThat(bobView.sharedItems()).isEqualTo(1);
  }

  @Test
  void kindredCurators_emptyForUnknownHandle() {
    assertThat(service.kindredCurators("no-such-curator-xyz", 12)).isEmpty();
  }

  @Test
  void relatedTo_resolvesHighlightCoMember_emptyWhenUnconnected_andClampsLimit() {
    // 하이라이트는 (post,span) 유니크라 같은 글에 같은 span 으로 둘 만들면 충돌 → 글을 따로 둔다.
    Long writer = user("writer-edge", "wed");
    Long h1 = highlight(post(writer, "e-s1"), writer, "씨앗 문장");
    Long h2 =
        highlight(post(writer, "e-s2"), writer, "곁에 놓인 다른 문장"); // HIGHLIGHT 공동 멤버 — resolve 갈래.
    Long lonely = highlight(post(writer, "e-s3"), writer, "아무 컬렉션에도 없는 문장");

    Long c = collection(writer, "E", CollectionVisibility.PUBLIC);
    connect(c, ConnectionBlockType.HIGHLIGHT, h1, 0);
    connect(c, ConnectionBlockType.HIGHLIGHT, h2, 1);

    // h2 가 (HIGHLIGHT) 로 해석돼 quote 가 채워진다(resolve 의 HIGHLIGHT 갈래).
    List<RelatedBlockView> related = service.relatedTo(ConnectionBlockType.HIGHLIGHT, h1, 24);
    RelatedBlockView h2View =
        related.stream()
            .filter(r -> r.blockType().equals("HIGHLIGHT") && r.refId().equals(h2))
            .findFirst()
            .orElseThrow();
    assertThat(h2View.quote()).isEqualTo("곁에 놓인 다른 문장");

    // clamp: limit 0 → 최소 1(예외 없이 ≤1개), limit 100 → 상한 24. 둘 다 호출만으로 갈래 커버.
    assertThat(service.relatedTo(ConnectionBlockType.HIGHLIGHT, h1, 0)).hasSizeLessThanOrEqualTo(1);
    // 어떤 공개 컬렉션에도 없는 블록 → 빈 결과(rows.isEmpty 조기 반환).
    assertThat(service.relatedTo(ConnectionBlockType.HIGHLIGHT, lonely, 100)).isEmpty();
  }

  @Test
  void kindredCurators_emptyWhenNoOneSharesTheirItems() {
    Long solo = user("solo-curator", "slo");
    Long p = post(solo, "s-one");
    Long c = collection(solo, "Solo", CollectionVisibility.PUBLIC);
    connect(c, ConnectionBlockType.POST, p, 0);

    // 공개 컬렉션은 있지만 그 블록을 엮은 다른 큐레이터가 없다 → 빈 결과(겹침 rows.isEmpty).
    assertThat(service.kindredCurators("solo-curator", 12)).isEmpty();
  }
}
