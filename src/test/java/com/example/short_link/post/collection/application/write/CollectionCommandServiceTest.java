package com.example.short_link.post.collection.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionCommandServiceTest {

  @Mock private CollectionRepository collectionRepository;
  @Mock private CollectionConnectionRepository connectionRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostHighlightRepository highlightRepository;
  @Mock private NoteRepository noteRepository;

  private CollectionCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new CollectionCommandService(
            collectionRepository,
            connectionRepository,
            postRepository,
            highlightRepository,
            noteRepository);
  }

  private CollectionEntity collection(long id, long ownerId, CollectionVisibility visibility) {
    CollectionEntity c = new CollectionEntity(ownerId, "느린 사고", null, visibility);
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
                1L, "  느린 사고  ", "  오래 머문 글  ", CollectionVisibility.PUBLIC));

    assertThat(saved.getOwnerId()).isEqualTo(1L);
    assertThat(saved.getTitle()).isEqualTo("느린 사고"); // stripped
    assertThat(saved.getDescription()).isEqualTo("오래 머문 글");
    assertThat(saved.getVisibility()).isEqualTo(CollectionVisibility.PUBLIC);
  }

  @Test
  void createDefaultsVisibilityToPrivateAndNullsBlankDescription() {
    when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionEntity saved = service.create(new CreateCollectionCommand(1L, "제목", "   ", null));

    assertThat(saved.getVisibility()).isEqualTo(CollectionVisibility.PRIVATE);
    assertThat(saved.getDescription()).isNull();
  }

  @Test
  void createRejectsBlankTitle() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateCollectionCommand(1L, "   ", null, CollectionVisibility.PRIVATE)))
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
            new CreateCollectionCommand(1L, tooLong, null, CollectionVisibility.PRIVATE));

    assertThat(saved.getTitle()).hasSize(CollectionEntity.MAX_TITLE);
  }

  // ---- connect ----

  @Test
  void connectAppendsAtNextPositionAfterValidatingTarget() {
    when(collectionRepository.findById(10L))
        .thenReturn(Optional.of(collection(10L, 1L, CollectionVisibility.PRIVATE)));
    PostEntity post = new PostEntity(2L, "slug", "Title", "ko");
    ReflectionTestUtils.setField(post, "id", 5L);
    when(postRepository.findById(5L)).thenReturn(Optional.of(post));
    when(connectionRepository.existsByCollectionIdAndBlockTypeAndRefId(
            10L, ConnectionBlockType.POST, 5L))
        .thenReturn(false);
    when(connectionRepository.findMaxPositionByCollectionId(10L)).thenReturn(3);
    when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CollectionConnectionEntity saved =
        service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.POST, 5L, "  좋은 글  "));

    assertThat(saved.getPosition()).isEqualTo(4); // max 3 + 1
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
    when(connectionRepository.existsByCollectionIdAndBlockTypeAndRefId(
            10L, ConnectionBlockType.NOTE, 7L))
        .thenReturn(false);
    when(connectionRepository.findMaxPositionByCollectionId(10L)).thenReturn(null);
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
    PostHighlightEntity hl = new PostHighlightEntity(6L, 1L, 0, 0, 3, "q");
    ReflectionTestUtils.setField(hl, "id", 9L);
    when(highlightRepository.findById(9L)).thenReturn(Optional.of(hl));
    when(connectionRepository.existsByCollectionIdAndBlockTypeAndRefId(
            10L, ConnectionBlockType.HIGHLIGHT, 9L))
        .thenReturn(true);
    CollectionConnectionEntity existing =
        new CollectionConnectionEntity(10L, ConnectionBlockType.HIGHLIGHT, 9L, "old", 2);
    when(connectionRepository.findAllByCollectionIdOrderByPositionAsc(10L))
        .thenReturn(List.of(existing));

    CollectionConnectionEntity result =
        service.connect(new ConnectBlockCommand(1L, 10L, ConnectionBlockType.HIGHLIGHT, 9L, "new"));

    assertThat(result).isSameAs(existing);
    verify(connectionRepository, never()).save(any());
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
}
