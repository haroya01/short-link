package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
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
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
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
    CollectionEntity c = new CollectionEntity(ownerId, "느린 사고", "오래 머문 글", visibility);
    ReflectionTestUtils.setField(c, "id", id);
    return c;
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

    List<CollectionSummaryView> views = service.listMine(1L);

    assertThat(views).hasSize(1);
    assertThat(views.get(0).id()).isEqualTo(10L);
    assertThat(views.get(0).title()).isEqualTo("느린 사고");
    assertThat(views.get(0).visibility()).isEqualTo("PUBLIC");
    assertThat(views.get(0).count()).isEqualTo(3);
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

    PostHighlightEntity hl = new PostHighlightEntity(6L, 3L, 0, 0, 3, "좋은 추상은 더 지울 게 없을 때");
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
}
