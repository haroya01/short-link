package com.example.short_link.post.collection.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.CollectionConnectedEvent;
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
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionCommandServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionConnectionRepository connectionRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private NoteRepository noteRepository;
  @Mock private ApplicationEventPublisher events;

  private CollectionCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new CollectionCommandService(
            collectionRepository,
            connectionRepository,
            postRepository,
            highlightRepository,
            noteRepository,
            events);
  }

  private CollectionEntity collection(long id, long ownerId, CollectionVisibility visibility) {
    CollectionEntity c =
        new CollectionEntity(ownerId, "느린 사고", null, visibility, CollectionKind.COLLECTION);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  // ---- create ----

  @Test
  void createsCollectionWithEchoedFields() {
    when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionEntity saved =
        service.create(
            new CreateCollectionCommand(
                1L, "  느린 사고  ", "  오래 머문 글  ", CollectionVisibility.PUBLIC, null));

    assertThat(saved.getOwnerId()).isEqualTo(1L);
    assertThat(saved.getTitle()).isEqualTo("느린 사고"); // stripped
    assertThat(saved.getDescription()).isEqualTo("오래 머문 글");
    assertThat(saved.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
    assertThat(saved.getKind()).isEqualTo(CollectionKind.COLLECTION); // null → 기본
  }

  @Test
  void createWithPathKindEchoesPath() {
    when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionEntity saved =
        service.create(
            new CreateCollectionCommand(
                1L, "읽기 경로", null, CollectionVisibility.PUBLIC, CollectionKind.PATH));

    assertThat(saved.getKind()).isEqualTo(CollectionKind.PATH);
  }

  @Test
  void createDefaultsVisibilityToPrivateAndNullsBlankDescription() {
    when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionEntity saved =
        service.create(new CreateCollectionCommand(1L, "제목", "   ", null, null));

    assertThat(saved.getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
    assertThat(saved.getDescription()).isNull();
  }

  @Test
  void createRejectsBlankTitle() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateCollectionCommand(
                        1L, "   ", null, CollectionVisibility.PRIVATE, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);
    verify(collectionRepository, never()).save(any());
  }

  @Test
  void createClampsTitleToColumnCap() {
    when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    String tooLong = "가".repeat(CollectionEntity.MAX_TITLE + 50);

    CollectionEntity saved =
        service.create(
            new CreateCollectionCommand(1L, tooLong, null, CollectionVisibility.PRIVATE, null));

    assertThat(saved.getTitle()).hasSize(CollectionEntity.MAX_TITLE);
  }

  // ---- edit ----

  @Test
  void editUpdatesFieldsWhenOwner() {
    CollectionEntity c = collection(10L, 1L, CollectionVisibility.PRIVATE);
    when(collectionRepository.findById(10L)).thenReturn(Optional.of(c));

    CollectionEntity result =
        service.edit(
            new EditCollectionCommand(1L, 10L, "  새 이름  ", "새 소개", CollectionVisibility.PUBLIC));

    assertThat(result.getTitle()).isEqualTo("새 이름"); // stripped
    assertThat(result.getDescription()).isEqualTo("새 소개");
    assertThat(result.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
  }

  @Test
  void editRejectsBlankTitle() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(
            () ->
                service.edit(
                    new EditCollectionCommand(1L, 10L, "  ", null, CollectionVisibility.PUBLIC)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_TITLE_REQUIRED);
  }

  @Test
  void editRejectsForeignOwner() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 2L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(
            () ->
                service.edit(
                    new EditCollectionCommand(1L, 10L, "x", null, CollectionVisibility.PUBLIC)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_PERMISSION_DENIED);
  }

  // ---- connect ----

  private CollectionConnectionEntity connection(
      long refId, ConnectionBlockType type, int position) {
    return new CollectionConnectionEntity(10L, type, refId, null, position);
  }

  @Test
  void connectAppendsAtNextPositionAfterValidatingTarget() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    PostEntity post = new PostEntity(2L, "slug", "Title", "ko");
    ReflectionTestUtils.setField(post, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(post));
    // The path already has three blocks; the new block appends after the highest position.
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                connection(101L, ConnectionBlockType.POST, 1),
                connection(102L, ConnectionBlockType.POST, 3),
                connection(103L, ConnectionBlockType.NOTE, 2)));
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionConnectionEntity saved =
        service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, "  좋은 글  "));

    assertThat(saved.getPosition()).isEqualTo(4); // max position 3 + 1
    assertThat(saved.getBlockType()).isEqualTo(ConnectionBlockType.POST);
    assertThat(saved.getWhy()).isEqualTo("좋은 글"); // stripped
  }

  @Test
  void connectStartsAtPositionZeroWhenEmpty() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    NoteEntity note = new NoteEntity(1L, "생각");
    ReflectionTestUtils.setField(note, "id", 7L);
    when(noteRepository.findById(7L)).thenReturn(Optional.of(note));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L)).thenReturn(List.of());
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionConnectionEntity saved =
        service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.NOTE, 7L, null));

    assertThat(saved.getPosition()).isZero();
    assertThat(saved.getWhy()).isNull();
  }

  @Test
  void connectIsIdempotentWhenAlreadyConnected() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    PostHighlightEntity hl = new PostHighlightEntity(6L, 1L, 0, 0, 0, 3, "q", null);
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findById(9L)).thenReturn(Optional.of(hl));
    CollectionConnectionEntity existing =
        new CollectionConnectionEntity(10L, ConnectionBlockType.HIGHLIGHT, 9L, "old", 2);
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(List.of(existing));

    CollectionConnectionEntity result =
        service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.HIGHLIGHT, 9L, "new"));

    assertThat(result).isSameAs(existing);
    verify(connectionRepository, never()).save(any());
    // Re-connecting an already-present block is idempotent: no graph notice fires.
    verify(events, never()).publishEvent(any());
  }

  // ---- connect → graph notification event ----

  private PostEntity post(long id, long authorId) {
    PostEntity p = new PostEntity(authorId, "s" + id, "T" + id, "ko");
    ReflectionTestUtils.setField(p, "id", id);
    return p;
  }

  private PostHighlightEntity highlight(long id, long authorId, long postId) {
    PostHighlightEntity h = new PostHighlightEntity(postId, authorId, 0, 0, 0, 3, "q", null);
    ReflectionTestUtils.setField(h, "id", id);
    return h;
  }

  private CollectionConnectedEvent capturePublishedEvent() {
    ArgumentCaptor<CollectionConnectedEvent> captor =
        ArgumentCaptor.forClass(CollectionConnectedEvent.class);
    verify(events).publishEvent(captor.capture());
    return captor.getValue();
  }

  @Test
  void connectingPostPublishesEventWithAuthorAndDedupedPriorContributors() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    // Curator 1 weaves post 5 (by author 2) into a path already holding posts by authors 3, 4, 3.
    when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, 2L)));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                connection(101L, ConnectionBlockType.POST, 0),
                connection(102L, ConnectionBlockType.POST, 1),
                connection(103L, ConnectionBlockType.POST, 2)));
    when(postRepository.findAllByIdIn(List.of(101L, 102L, 103L)))
        .thenReturn(List.of(post(101L, 3L), post(102L, 4L), post(103L, 3L)));
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null));

    CollectionConnectedEvent event = capturePublishedEvent();
    assertThat(event.actorUserId()).isEqualTo(1L);
    assertThat(event.collectionId()).isEqualTo(10L);
    assertThat(event.collectionName()).isEqualTo("느린 사고");
    assertThat(event.connectedPostId()).isEqualTo(5L); // a post occasions itself
    assertThat(event.connectedAuthorUserId()).isEqualTo(2L);
    // 3 appears twice among prior blocks → deduped to one; order preserved.
    assertThat(event.priorContributorUserIds()).containsExactly(3L, 4L);
  }

  @Test
  void priorContributorsExcludeTheWovenAuthorAndTheCurator() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    // Curator 1 weaves post 5 (by author 2); the path already holds work by 2, by 1, and by 9.
    when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, 2L)));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                connection(101L, ConnectionBlockType.POST, 0),
                connection(102L, ConnectionBlockType.POST, 1),
                connection(103L, ConnectionBlockType.POST, 2)));
    when(postRepository.findAllByIdIn(List.of(101L, 102L, 103L)))
        .thenReturn(List.of(post(101L, 2L), post(102L, 1L), post(103L, 9L)));
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null));

    CollectionConnectedEvent event = capturePublishedEvent();
    // 2 (woven author) and 1 (curator) drop out; only 9 remains.
    assertThat(event.priorContributorUserIds()).containsExactly(9L);
  }

  @Test
  void connectingHighlightUsesHighlighterAsAuthorAndItsPostAsOccasion() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    // Highlight 5 was made by reader 8 on post 77 — the highlighter is the author to notify.
    when(highlightRepository.findById(5L)).thenReturn(Optional.of(highlight(5L, 8L, 77L)));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L)).thenReturn(List.of());
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.HIGHLIGHT, 5L, null));

    CollectionConnectedEvent event = capturePublishedEvent();
    assertThat(event.connectedAuthorUserId()).isEqualTo(8L);
    assertThat(event.connectedPostId()).isEqualTo(77L); // the post the highlight sits on
    assertThat(event.priorContributorUserIds()).isEmpty();
  }

  @Test
  void connectingHighlightResolvesPriorContributorsFromHighlightAuthorsInBulk() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, 2L)));
    // The path already holds a post (by 3) and two highlights (by 4 and 4).
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                connection(201L, ConnectionBlockType.POST, 0),
                connection(202L, ConnectionBlockType.HIGHLIGHT, 1),
                connection(203L, ConnectionBlockType.HIGHLIGHT, 2)));
    when(postRepository.findAllByIdIn(List.of(201L))).thenReturn(List.of(post(201L, 3L)));
    when(highlightRepository.findAllByIdIn(List.of(202L, 203L)))
        .thenReturn(List.of(highlight(202L, 4L, 77L), highlight(203L, 4L, 78L)));
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null));

    CollectionConnectedEvent event = capturePublishedEvent();
    // Post author 3 + highlight author 4 (deduped across the two highlights).
    assertThat(event.priorContributorUserIds()).containsExactly(3L, 4L);
  }

  @Test
  void connectingNoteHasNoConnectedAuthorAndNotesDoNotContributeToPathGrew() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PUBLIC)));
    NoteEntity note = new NoteEntity(1L, "생각");
    ReflectionTestUtils.setField(note, "id", 5L);
    when(noteRepository.findById(5L)).thenReturn(Optional.of(note));
    // The path already holds a post (by 3) and a note — the note must not become a contributor.
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(
            List.of(
                connection(301L, ConnectionBlockType.POST, 0),
                connection(302L, ConnectionBlockType.NOTE, 1)));
    when(postRepository.findAllByIdIn(List.of(301L))).thenReturn(List.of(post(301L, 3L)));
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.NOTE, 5L, null));

    CollectionConnectedEvent event = capturePublishedEvent();
    assertThat(event.connectedAuthorUserId()).isNull(); // a note's author is its curator
    assertThat(event.connectedPostId()).isNull(); // a note has no post
    assertThat(event.priorContributorUserIds()).containsExactly(3L); // note 302 excluded
  }

  @Test
  void connectRejectsMissingTarget() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    when(postRepository.findById(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.connect(
                    new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.CONNECTION_TARGET_NOT_FOUND);
    verify(connectionRepository, never()).save(any());
  }

  @Test
  void connectRejectsForeignCollection() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 2L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(
            () ->
                service.connect(
                    new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_PERMISSION_DENIED);
  }

  @Test
  void connectThrowsWhenCollectionMissing() {
    when(collectionRepository.findById(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.connect(
                    new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, null)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_NOT_FOUND);
  }

  // ---- disconnect ----

  @Test
  void disconnectRemovesOwnConnection() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    CollectionConnectionEntity conn =
        new CollectionConnectionEntity(10L, ConnectionBlockType.POST, 5L, null, 0);
    ReflectionTestUtils.setField(conn, "id", 33L);
    when(connectionRepository.findById(33L)).thenReturn(Optional.of(conn));

    service.disconnect(1L, 10L, 33L);

    verify(connectionRepository).delete(conn);
  }

  @Test
  void disconnectRejectsConnectionOfAnotherCollection() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    CollectionConnectionEntity foreign =
        new CollectionConnectionEntity(99L, ConnectionBlockType.POST, 5L, null, 0);
    ReflectionTestUtils.setField(foreign, "id", 33L);
    when(connectionRepository.findById(33L)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.disconnect(1L, 10L, 33L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.CONNECTION_NOT_FOUND);
    verify(connectionRepository, never()).delete(any());
  }

  // ---- delete ----

  @Test
  void deleteCollectionRemovesWhenOwner() {
    CollectionEntity c = collection(10L, 1L, CollectionVisibility.PRIVATE);
    when(collectionRepository.findById(10L)).thenReturn(Optional.of(c));

    service.deleteCollection(1L, 10L);

    verify(collectionRepository).delete(c);
  }

  @Test
  void deleteCollectionRejectsForeignOwner() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 2L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(() -> service.deleteCollection(1L, 10L))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_PERMISSION_DENIED);
    verify(collectionRepository, never()).delete(any());
  }

  // ---- reorder ----

  private CollectionConnectionEntity conn(long id, int position) {
    CollectionConnectionEntity c =
        new CollectionConnectionEntity(10L, ConnectionBlockType.HIGHLIGHT, id, null, position);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
  }

  @Test
  void reorderReassignsPositionsInGivenOrder() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    CollectionConnectionEntity a = conn(101L, 0);
    CollectionConnectionEntity b = conn(102L, 1);
    CollectionConnectionEntity c = conn(103L, 2);
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(List.of(a, b, c));

    service.reorder(1L, 10L, List.of(103L, 101L, 102L));

    assertThat(c.getPosition()).isZero();
    assertThat(a.getPosition()).isEqualTo(1);
    assertThat(b.getPosition()).isEqualTo(2);
  }

  @Test
  void reorderRejectsMismatchedIdSet() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(List.of(conn(101L, 0), conn(102L, 1)));

    assertThatThrownBy(() -> service.reorder(1L, 10L, List.of(101L, 999L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_REORDER_MISMATCH);
  }

  @Test
  void reorderRejectsForeignCollection() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 2L, CollectionVisibility.PRIVATE)));

    assertThatThrownBy(() -> service.reorder(1L, 10L, List.of(1L)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COLLECTION_PERMISSION_DENIED);
  }
}
