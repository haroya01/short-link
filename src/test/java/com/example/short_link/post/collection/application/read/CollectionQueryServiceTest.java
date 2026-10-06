package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.common.note.NoteBodyReader;
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
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionQueryServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionConnectionRepository connectionRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private NoteBodyReader noteBodies;
  @Mock private UserRepository userRepository;

  private CollectionQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new CollectionQueryService(
            collectionRepository,
            connectionRepository,
            new CollectionContentReader(
                connectionRepository,
                postRepository,
                highlightRepository,
                noteBodies,
                userRepository),
            userRepository);
  }

  private CollectionEntity collection(long id, long ownerId, CollectionVisibility visibility) {
    CollectionEntity c =
        new CollectionEntity(ownerId, "느린 사고", "오래 머문 글", visibility, CollectionKind.COLLECTION);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  @Test
  void editedSummaryUsesSavedFieldsAndCurrentCountWithoutLoadingListContent() {
    CollectionEntity saved = collection(10L, 1L, CollectionVisibility.PUBLIC);
    Instant updatedAt = Instant.parse("2026-09-11T00:00:00Z");
    ReflectionTestUtils.setField(saved, "updatedAt", updatedAt);
    when(connectionRepository.countByCollectionId(10L)).thenReturn(3L);

    CollectionSummaryView summary = service.editedSummary(saved);

    assertThat(summary.id()).isEqualTo(10L);
    assertThat(summary.title()).isEqualTo(saved.getTitle());
    assertThat(summary.updatedAt()).isEqualTo(updatedAt);
    assertThat(summary.count()).isEqualTo(3);
    assertThat(summary.preview()).isEmpty();
    assertThat(summary.curatorUsername()).isNull();
    assertThat(summary.curatorAvatarUrl()).isNull();
    assertThat(summary.position()).isNull();
    assertThat(summary.connectionId()).isNull();
    verifyNoInteractions(
        collectionRepository, postRepository, highlightRepository, noteBodies, userRepository);
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
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "curator")));
    when(connectionRepository.findRanksByCollectionIdsAndBlockType(
            anyCollection(), eq(ConnectionBlockType.POST)))
        .thenReturn(List.of(new CollectionConnectionRank(20L, 5L, 3)));

    List<CollectionSummaryView> result =
        service.publicCollectionsContaining(ConnectionBlockType.POST, 5L);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo(20L);
    assertThat(result.get(0).visibility()).isEqualTo("PUBLIC");
    assertThat(result.get(0).count()).isEqualTo(4);
    assertThat(result.get(0).curatorUsername()).isEqualTo("curator");
    assertThat(result.get(0).position()).isEqualTo(3);
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
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(1L, "curator")));
    when(connectionRepository.findRanksByCollectionIdsAndBlockType(
            anyCollection(), eq(ConnectionBlockType.POST)))
        .thenReturn(
            List.of(
                new CollectionConnectionRank(10L, 5L, 1),
                new CollectionConnectionRank(10L, 6L, 2)));

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(ConnectionBlockType.POST, List.of(5L, 6L, 7L));

    assertThat(result).containsOnlyKeys(5L, 6L, 7L);
    assertThat(result.get(5L)).extracting(CollectionSummaryView::id).containsExactly(10L);
    assertThat(result.get(5L).get(0).count()).isEqualTo(4);
    assertThat(result.get(5L).get(0).curatorUsername()).isEqualTo("curator");
    assertThat(result.get(5L).get(0).position()).isEqualTo(1);
    assertThat(result.get(6L)).extracting(CollectionSummaryView::id).containsExactly(10L);
    assertThat(result.get(6L).get(0).position()).isEqualTo(2);
    assertThat(result.get(7L)).isEmpty();

    InOrder queries = inOrder(connectionRepository, collectionRepository, userRepository);
    queries
        .verify(connectionRepository)
        .findAllByBlockTypeAndRefIdIn(ConnectionBlockType.POST, List.of(5L, 6L, 7L));
    queries.verify(collectionRepository).findAllByIdIn(Set.of(10L, 11L));
    queries.verify(connectionRepository).countByCollectionIdIn(Set.of(10L));
    queries.verify(userRepository).findAllByIdIn(Set.of(1L));
    queries
        .verify(connectionRepository)
        .findRanksByCollectionIdsAndBlockType(Set.of(10L), ConnectionBlockType.POST);
    queries.verifyNoMoreInteractions();
  }

  @Test
  void batchKeepsRequestedOrderAndRecentCollectionsWithStableTiesAndNoDuplicates() {
    CollectionEntity older = collection(10L, 1L, CollectionVisibility.PUBLIC);
    CollectionEntity recent = collection(12L, 1L, CollectionVisibility.PUBLIC);
    CollectionEntity recentEncounteredFirst = collection(13L, 1L, CollectionVisibility.PUBLIC);
    ReflectionTestUtils.setField(older, "updatedAt", Instant.parse("2026-09-10T00:00:00Z"));
    ReflectionTestUtils.setField(recent, "updatedAt", Instant.parse("2026-09-11T00:00:00Z"));
    ReflectionTestUtils.setField(recentEncounteredFirst, "updatedAt", recent.getUpdatedAt());
    when(connectionRepository.findAllByBlockTypeAndRefIdIn(
            ConnectionBlockType.POST, List.of(7L, 5L, 6L)))
        .thenReturn(
            List.of(
                conn(10L, ConnectionBlockType.POST, 5L),
                conn(13L, ConnectionBlockType.POST, 5L),
                conn(12L, ConnectionBlockType.POST, 5L),
                conn(13L, ConnectionBlockType.POST, 5L),
                conn(10L, ConnectionBlockType.POST, 6L)));
    when(collectionRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(older, recent, recentEncounteredFirst));

    Map<Long, List<CollectionSummaryView>> result =
        service.publicCollectionsContainingBatch(
            ConnectionBlockType.POST, Arrays.asList(7L, null, 5L, 5L, 6L));

    assertThat(result.keySet()).containsExactly(7L, 5L, 6L);
    assertThat(result.get(7L)).isEmpty();
    assertThat(result.get(5L)).extracting(CollectionSummaryView::id).containsExactly(13L, 12L, 10L);
    assertThat(result.get(6L)).extracting(CollectionSummaryView::id).containsExactly(10L);
    assertThat(result.get(5L))
        .allSatisfy(
            summary -> {
              assertThat(summary.count()).isZero();
              assertThat(summary.preview()).isEmpty();
              assertThat(summary.curatorUsername()).isNull();
              assertThat(summary.curatorAvatarUrl()).isNull();
              assertThat(summary.position()).isNull();
              assertThat(summary.connectionId()).isNull();
            });
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
    ReflectionTestUtils.setField(p, "id", id);
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
    when(connectionRepository.findAllByCollectionIdInOrderByPositionDesc(anyCollection()))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 2),
                conn(101L, ConnectionBlockType.NOTE, 7L, 1),
                conn(102L, ConnectionBlockType.POST, 6L, 0)));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(post(5L, 2L)));
    when(noteBodies.blocksByIds(anyCollection()))
        .thenReturn(Map.of(7L, new NoteBlock(7L, "더 나은 질문을 기다리는 일", "note_author")));
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
                conn(201L, ConnectionBlockType.POST, 999L, 0)));
    String longQuote = "가".repeat(60);
    PostHighlightEntity hl = new PostHighlightEntity(6L, 1L, 0, 0, 0, 3, longQuote, null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    when(postRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());
    when(noteBodies.blocksByIds(anyCollection())).thenReturn(Map.of());

    List<CollectionSummaryView> views = service.listMine(1L, null, null);

    assertThat(views.get(0).preview()).hasSize(1);
    assertThat(views.get(0).preview().get(0)).endsWith("…").hasSize(41);
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
    when(noteBodies.blocksByIds(anyCollection()))
        .thenReturn(Map.of(7L, new NoteBlock(7L, "더 나은 질문을 기다리는 일", "note_author")));
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 2L), post(6L, 3L)));
    when(userRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(user(2L, "alice"), user(3L, "bob")));
    when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "curator")));

    CollectionDetailView view = service.detail(1L, 10L);

    assertThat(view.curatorUsername()).isEqualTo("curator");
    assertThat(view.connections()).hasSize(3);
    ConnectionView postView = view.connections().get(0);
    assertThat(postView.blockType()).isEqualTo("POST");
    assertThat(postView.title()).isEqualTo("Title 5");
    assertThat(postView.username()).isEqualTo("alice");
    ConnectionView hlView = view.connections().get(1);
    assertThat(hlView.blockType()).isEqualTo("HIGHLIGHT");
    assertThat(hlView.quote()).isEqualTo("좋은 추상은 더 지울 게 없을 때");
    assertThat(hlView.title()).isEqualTo("Title 6");
    assertThat(hlView.username()).isEqualTo("bob");
    ConnectionView noteView = view.connections().get(2);
    assertThat(noteView.blockType()).isEqualTo("NOTE");
    assertThat(noteView.body()).isEqualTo("더 나은 질문을 기다리는 일");
  }

  // 미발행 글은 연결이 남아 있어도 공개 컬렉션 상세에 제목·발췌·인용이 새지 않아야 한다.
  @Test
  void detailHidesUnpublishedConnectedPosts() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                conn(100L, ConnectionBlockType.POST, 5L, 0),
                conn(101L, ConnectionBlockType.POST, 6L, 1),
                conn(102L, ConnectionBlockType.HIGHLIGHT, 9L, 2)));
    PostHighlightEntity hl = new PostHighlightEntity(8L, 3L, 0, 0, 0, 3, "차단된 글의 인용", null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hl));
    when(noteBodies.blocksByIds(anyCollection())).thenReturn(Map.of());
    when(postRepository.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(5L, 2L), draftPost(6L, 2L), draftPost(8L, 3L)));
    when(userRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(user(2L, "alice")));
    when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "curator")));

    CollectionDetailView view = service.detail(1L, 10L);

    assertThat(view.connections()).hasSize(1);
    assertThat(view.connections().get(0).blockType()).isEqualTo("POST");
    assertThat(view.connections().get(0).title()).isEqualTo("Title 5");
  }

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
    when(noteBodies.blocksByIds(anyCollection())).thenReturn(Map.of());

    List<CollectionSummaryView> result = service.listPublicByUsername("curator");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).preview()).containsExactly("Title 5");
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
