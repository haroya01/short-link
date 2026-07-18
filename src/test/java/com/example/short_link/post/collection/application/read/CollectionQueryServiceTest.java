package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.post.collection.domain.CollectionConnectionCount;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionConnectionRank;
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
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionQueryServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionConnectionRepository connectionRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private NoteRepository noteRepository;
  @Mock private UserRepository userRepository;

  private CollectionQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new CollectionQueryService(
            collectionRepository,
            connectionRepository,
            postRepository,
            highlightRepository,
            noteRepository,
            userRepository);
  }

  private CollectionEntity collection(long id, long ownerId, CollectionVisibility visibility) {
    CollectionEntity c =
        new CollectionEntity(ownerId, "느린 사고", "오래 머문 글", visibility, CollectionKind.COLLECTION);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  @Test
  void publicCollectionsContainingReturnsOnlyPublic() {
    when(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.HIGHLIGHT, 9L))
        .thenReturn(
            List.of(
                new CollectionConnectionEntity(10L, ConnectionBlockType.HIGHLIGHT, 9L, null, 0),
                new CollectionConnectionEntity(11L, ConnectionBlockType.HIGHLIGHT, 9L, null, 0)));
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(collectionRepository.findById(11L))
        .thenReturn(Optional.of(collection(11L, 1L, CollectionVisibility.PRIVATE)));
    when(connectionRepository.countByCollectionId(10L)).thenReturn(3L);

    List<CollectionSummaryView> result =
        service.publicCollectionsContaining(ConnectionBlockType.HIGHLIGHT, 9L);

    // 비공개(11)는 빠지고 공개(10)만, count·kind 채워서.
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(10L);
    assertThat(result.get(0).count()).isEqualTo(3);
    assertThat(result.get(0).kind()).isEqualTo("COLLECTION");
  }

  @Test
  void publicCollectionsContainingPostReturnsOnlyPublic() {
    when(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, 5L))
        .thenReturn(
            List.of(
                new CollectionConnectionEntity(20L, ConnectionBlockType.POST, 5L, null, 0),
                new CollectionConnectionEntity(21L, ConnectionBlockType.POST, 5L, null, 0)));
    when(collectionRepository.findById(20L))
        .thenReturn(Optional.of(collection(20L, 1L, CollectionVisibility.PUBLIC)));
    when(collectionRepository.findById(21L))
        .thenReturn(Optional.of(collection(21L, 1L, CollectionVisibility.PRIVATE)));
    when(connectionRepository.countByCollectionId(20L)).thenReturn(4L);
    // 큐레이터(소유자 1) 벌크 조회 + 컬렉션 20 안에서 글 5 의 1-based position(3번째).
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "curator")));
    when(connectionRepository.findRanksByCollectionIdsAndBlockType(
            anyCollection(), eq(ConnectionBlockType.POST)))
        .thenReturn(List.of(new CollectionConnectionRank(20L, 5L, 3)));

    List<CollectionSummaryView> result =
        service.publicCollectionsContaining(ConnectionBlockType.POST, 5L);

    // 글(POST) 타입으로도 비공개(21)는 빠지고 공개(20)만 흐른다. 큐레이터·위치가 함께 실린다.
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(20L);
    assertThat(result.get(0).visibility()).isEqualTo("PUBLIC");
    assertThat(result.get(0).count()).isEqualTo(4);
    assertThat(result.get(0).curatorUsername()).isEqualTo("curator");
    assertThat(result.get(0).position()).isEqualTo(3); // "4편 중 3번째"
  }

  @Test
  void publicCollectionsContainingEmptyWhenNoConnections() {
    when(connectionRepository.findAllByBlockTypeAndRefId(ConnectionBlockType.POST, 99L))
        .thenReturn(List.of());

    assertThat(service.publicCollectionsContaining(ConnectionBlockType.POST, 99L)).isEmpty();
  }

  private CollectionConnectionEntity conn(long collectionId, ConnectionBlockType type, long refId) {
    return new CollectionConnectionEntity(collectionId, type, refId, null, 0);
  }

  @Test
  void batchGroupsPublicCollectionsPerPost_oneQueryEach() {
    // p5 → 공개 C10 + 비공개 C11, p6 → 공개 C10(같은 컬렉션에도 담김), p7 → 아무 컬렉션에도 없음.
    when(connectionRepository.findAllByBlockTypeAndRefIdIn(
            ConnectionBlockType.POST, List.of(5L, 6L, 7L)))
        .thenReturn(
            List.of(
                conn(10L, ConnectionBlockType.POST, 5L),
                conn(11L, ConnectionBlockType.POST, 5L),
                conn(10L, ConnectionBlockType.POST, 6L)));
    when(collectionRepository.findAllByIdIn(anyCollection()))
        .thenReturn(
            List.of(
                collection(10L, 1L, CollectionVisibility.PUBLIC),
                collection(11L, 1L, CollectionVisibility.PRIVATE)));
    when(connectionRepository.countByCollectionIdIn(anyCollection()))
        .thenReturn(List.of(new CollectionConnectionCount(10L, 4L)));
    // 공개 C10 소유자(큐레이터 1) 벌크 조회 + C10 안에서 글 5·6 의 1-based position.
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "curator")));
    when(connectionRepository.findRanksByCollectionIdsAndBlockType(
            anyCollection(), eq(ConnectionBlockType.POST)))
        .thenReturn(
            List.of(
                new CollectionConnectionRank(10L, 5L, 1),
                new CollectionConnectionRank(10L, 6L, 2)));

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L, 6L, 7L));

    // 요청한 세 글 모두 응답에 있고, 비공개 C11 은 빠진다.
    assertThat(result).containsOnlyKeys(5L, 6L, 7L);
    assertThat(result.get(5L)).extracting(CollectionSummaryView::id).containsExactly(10L);
    assertThat(result.get(5L).get(0).count()).isEqualTo(4);
    // 같은 컬렉션 C10 이지만 글마다 다른 위치가 실린다(글 5 = 1번째, 글 6 = 2번째).
    assertThat(result.get(5L).get(0).curatorUsername()).isEqualTo("curator");
    assertThat(result.get(5L).get(0).position()).isEqualTo(1);
    assertThat(result.get(6L)).extracting(CollectionSummaryView::id).containsExactly(10L);
    assertThat(result.get(6L).get(0).position()).isEqualTo(2);
    assertThat(result.get(7L)).isEmpty(); // 어느 공개 컬렉션에도 없음 → 빈 올.
  }

  @Test
  void batchEmptyIdsReturnsEmptyMap_noQueries() {
    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void batchAllPrivateYieldsEmptyListsPerRequestedId() {
    when(connectionRepository.findAllByBlockTypeAndRefIdIn(ConnectionBlockType.POST, List.of(5L)))
        .thenReturn(List.of(conn(11L, ConnectionBlockType.POST, 5L)));
    when(collectionRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(collection(11L, 1L, CollectionVisibility.PRIVATE)));

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L));

    // 담긴 컬렉션이 전부 비공개면 요청한 id 는 빈 목록으로 남는다(빠지지 않음).
    assertThat(result).containsOnlyKeys(5L);
    assertThat(result.get(5L)).isEmpty();
  }

  @Test
  void batchDeduplicatesRequestedIds() {
    when(connectionRepository.findAllByBlockTypeAndRefIdIn(ConnectionBlockType.POST, List.of(5L)))
        .thenReturn(List.of(conn(10L, ConnectionBlockType.POST, 5L)));
    when(collectionRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(connectionRepository.countByCollectionIdIn(anyCollection()))
        .thenReturn(List.of(new CollectionConnectionCount(10L, 1L)));

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L, 5L, 5L));

    // 중복 id 는 한 번만 조회된다(distinct).
    assertThat(result).containsOnlyKeys(5L);
    assertThat(result.get(5L)).extracting(CollectionSummaryView::id).containsExactly(10L);
  }

  private CollectionConnectionEntity conn(long id, ConnectionBlockType type, long refId, int pos) {
    CollectionConnectionEntity c = new CollectionConnectionEntity(10L, type, refId, "왜", pos);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  private PostEntity post(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    p.publish();
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostEntity draftPost(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "slug-" + id, "Title " + id, "ko");
    ReflectionTestUtils.setField(p, "id", id); // status 는 DRAFT 그대로 — 미발행.
    return p;
  }

  private UserEntity user(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void listMineMapsSummariesWithCounts() {
    when(collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(1L))
        .thenReturn(List.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(connectionRepository.countByCollectionId(10L)).thenReturn(3L);
    // 최신순 연결 — 상위 2개만 미리보기에 든다(POST 제목 + NOTE 본문).
    when(connectionRepository.findAllByCollectionIdInOrderByPositionDesc(anyCollection()))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 2),
                conn(101L, ConnectionBlockType.NOTE, 7L, 1),
                conn(102L, ConnectionBlockType.POST, 6L, 0))); // 3번째는 제외
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(post(5L, 2L)));
    NoteEntity note = new NoteEntity(1L, "더 나은 질문을 기다리는 일");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

    List<CollectionSummaryView> views = service.listMine(1L, null, null);

    assertThat(views).hasSize(1);
    assertThat(views.get(0).id()).isEqualTo(10L);
    assertThat(views.get(0).title()).isEqualTo("느린 사고");
    assertThat(views.get(0).visibility()).isEqualTo("PUBLIC");
    assertThat(views.get(0).count()).isEqualTo(3);
    assertThat(views.get(0).preview()).containsExactly("Title 5", "더 나은 질문을 기다리는 일");
  }

  @Test
  void listMinePreviewResolvesHighlightTruncatesAndSkipsMissing() {
    when(collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(1L))
        .thenReturn(List.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(connectionRepository.countByCollectionId(10L)).thenReturn(2L);
    when(connectionRepository.findAllByCollectionIdInOrderByPositionDesc(anyCollection()))
        .thenReturn(
            List.of(
                conn(200L, ConnectionBlockType.HIGHLIGHT, 9L, 1),
                conn(201L, ConnectionBlockType.POST, 999L, 0))); // 원문 글 사라짐 → 라벨 null → 제외
    String longQuote = "가".repeat(60);
    PostHighlightEntity hl = new PostHighlightEntity(6L, 1L, 0, 0, 0, 3, longQuote, null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of()); // 999 없음
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

    List<CollectionSummaryView> views = service.listMine(1L, null, null);

    assertThat(views.get(0).preview()).hasSize(1); // 사라진 글 행은 빠짐
    assertThat(views.get(0).preview().get(0)).endsWith("…").hasSize(41); // 40자 + …
  }

  @Test
  void detailHidesPrivateCollectionFromNonOwner() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(() -> service.detail(2L, 10L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_NOT_FOUND);
  }

  @Test
  void detailThrowsWhenMissing() {
    when(collectionRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.detail(1L, 10L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_NOT_FOUND);
  }

  @Test
  void detailResolvesMixedBlocksAndSkipsMissingTargets() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    // POST(post 5) | HIGHLIGHT(hl 9 → post 6) | NOTE(note 7) | POST(post 999 missing → skipped)
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 0),
                conn(101L, ConnectionBlockType.HIGHLIGHT, 9L, 1),
                conn(102L, ConnectionBlockType.NOTE, 7L, 2),
                conn(103L, ConnectionBlockType.POST, 999L, 3)));

    PostHighlightEntity hl =
        new PostHighlightEntity(6L, 3L, 0, 0, 0, 3, "좋은 추상은 더 지울 게 없을 때", null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    NoteEntity note = new NoteEntity(1L, "더 나은 질문을 기다리는 일");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(note));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 2L), post(6L, 3L))); // 999 absent
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "alice"), user(3L, "bob")));
    when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "curator")));

    CollectionDetailView view = service.detail(1L, 10L);

    assertThat(view.curatorUsername()).isEqualTo("curator");
    assertThat(view.connections()).hasSize(3); // 999 skipped
    ConnectionView postView = view.connections().get(0);
    assertThat(postView.blockType()).isEqualTo("POST");
    assertThat(postView.title()).isEqualTo("Title 5");
    assertThat(postView.username()).isEqualTo("alice");
    ConnectionView hlView = view.connections().get(1);
    assertThat(hlView.blockType()).isEqualTo("HIGHLIGHT");
    assertThat(hlView.quote()).isEqualTo("좋은 추상은 더 지울 게 없을 때");
    assertThat(hlView.title()).isEqualTo("Title 6"); // resolved via highlight's post
    assertThat(hlView.username()).isEqualTo("bob");
    ConnectionView noteView = view.connections().get(2);
    assertThat(noteView.blockType()).isEqualTo("NOTE");
    assertThat(noteView.body()).isEqualTo("더 나은 질문을 기다리는 일");
  }

  // 미발행 글(초안·비공개·관리자 차단)은 공개 컬렉션 상세에서 빠진다 — 담김 연결이 남아 있어도 메타(제목·발췌·슬러그)와
  // 그 원문을 가리키는 하이라이트 인용까지 새지 않게. 단건 공개 read 가 미발행을 숨기는 것과 같은 규칙.
  @Test
  void detailHidesUnpublishedConnectedPosts() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    // POST(발행 5) | POST(초안 6) | HIGHLIGHT(hl 9 → 초안 원문 8)
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 0),
                conn(101L, ConnectionBlockType.POST, 6L, 1),
                conn(102L, ConnectionBlockType.HIGHLIGHT, 9L, 2)));
    PostHighlightEntity hl = new PostHighlightEntity(8L, 3L, 0, 0, 0, 3, "차단된 글의 인용", null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 2L), draftPost(6L, 2L), draftPost(8L, 3L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "alice")));
    when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "curator")));

    CollectionDetailView view = service.detail(1L, 10L);

    // 발행 글(5)만 남고, 초안 글(6)과 초안 원문을 가리키는 하이라이트(9)는 인용째로 빠진다.
    assertThat(view.connections()).hasSize(1);
    assertThat(view.connections().get(0).blockType()).isEqualTo("POST");
    assertThat(view.connections().get(0).title()).isEqualTo("Title 5");
  }

  // 공개 표면(다른 큐레이터의 공개 컬렉션)의 프리뷰 라벨엔 미발행 글 제목이 새지 않는다.
  @Test
  void listPublicByUsernameHidesUnpublishedPreviewLabels() {
    when(userRepository.findByUsername("curator")).thenReturn(Optional.of(user(1L, "curator")));
    when(collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(1L))
        .thenReturn(List.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(connectionRepository.countByCollectionId(10L)).thenReturn(2L);
    when(connectionRepository.findAllByCollectionIdInOrderByPositionDesc(anyCollection()))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 1),
                conn(101L, ConnectionBlockType.POST, 6L, 0)));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 1L), draftPost(6L, 1L)));
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(noteRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

    List<CollectionSummaryView> result = service.listPublicByUsername("curator");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).preview()).containsExactly("Title 5"); // 초안(6) 제목은 빠짐.
  }

  @Test
  void listPublicByUsernameReturnsOnlyThatCuratorsPublicCollections() {
    when(userRepository.findByUsername("curator")).thenReturn(Optional.of(user(1L, "curator")));
    when(collectionRepository.findAllByOwnerIdOrderByUpdatedAtDesc(1L))
        .thenReturn(
            List.of(
                collection(10L, 1L, CollectionVisibility.PUBLIC),
                collection(11L, 1L, CollectionVisibility.PRIVATE)));
    when(connectionRepository.countByCollectionId(10L)).thenReturn(2L);
    when(connectionRepository.findAllByCollectionIdInOrderByPositionDesc(anyCollection()))
        .thenReturn(List.of());

    List<CollectionSummaryView> result = service.listPublicByUsername("curator");

    // 비공개(11)는 빠지고 공개(10)만.
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(10L);
    assertThat(result.get(0).visibility()).isEqualTo("PUBLIC");
    assertThat(result.get(0).count()).isEqualTo(2);
  }

  @Test
  void listPublicByUsernameEmptyForUnknownHandle() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThat(service.listPublicByUsername("ghost")).isEmpty();
  }
}
