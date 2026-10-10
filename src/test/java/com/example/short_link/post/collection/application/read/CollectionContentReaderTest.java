package com.example.short_link.post.collection.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.post.collection.domain.CollectionConnectionEntity;
import com.example.short_link.post.collection.domain.CollectionEntity;
import com.example.short_link.post.collection.domain.CollectionVisibility;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import com.example.short_link.post.collection.domain.repository.CollectionConnectionRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CollectionContentReaderTest {
  @Mock private CollectionConnectionRepository connections;
  @Mock private PostRepository posts;
  @Mock private PostHighlightRepository highlights;
  @Mock private NoteBodyReader noteBodies;
  @Mock private UserRepository users;
  private CollectionContentReader reader;

  @BeforeEach
  void setUp() {
    reader = new CollectionContentReader(connections, posts, highlights, noteBodies, users);
  }

  @Test
  void publicPreviewHidesDraftAndItsQuoteWithoutFillingFromOlderConnections() {
    when(connections.findAllByCollectionIdInOrderByPositionDesc(List.of(1L)))
        .thenReturn(
            List.of(
                connection(1L, ConnectionBlockType.POST, 11L, 3),
                connection(2L, ConnectionBlockType.HIGHLIGHT, 20L, 2),
                connection(3L, ConnectionBlockType.POST, 13L, 1)));
    when(posts.findAllByIdIn(List.of(11L))).thenReturn(List.of(post(11L, false)));
    when(highlights.findAllByIdIn(List.of(20L))).thenReturn(List.of(highlight(20L, 12L)));
    when(posts.findAllByIdIn(Set.of(12L))).thenReturn(List.of(post(12L, false)));

    assertThat(reader.previewByCollection(List.of(collection(1L, false)), true))
        .containsEntry(1L, List.of());
    verify(posts).findAllByIdIn(List.of(11L));
    verify(posts).findAllByIdIn(Set.of(12L));
    verifyNoMoreInteractions(posts);
    verifyNoInteractions(users);
  }

  @Test
  void anOrderedCollectionPreviewsItsFirstTwoStepsAndAnUnorderedOneItsLatestTwo() {
    when(connections.findAllByCollectionIdInOrderByPositionDesc(List.of(1L, 2L)))
        .thenReturn(
            List.of(
                postIn(1L, 13L, 2),
                postIn(1L, 12L, 1),
                postIn(1L, 11L, 0),
                postIn(2L, 23L, 2),
                postIn(2L, 22L, 1),
                postIn(2L, 21L, 0)));
    when(posts.findAllByIdIn(anyCollection()))
        .thenReturn(List.of(post(11L, true), post(12L, true), post(22L, true), post(23L, true)));

    var previews =
        reader.previewByCollection(List.of(collection(1L, true), collection(2L, false)), true);

    assertThat(previews.get(1L)).containsExactly("Post 11", "Post 12");
    assertThat(previews.get(2L)).containsExactly("Post 23", "Post 22");
  }

  @Test
  void detailKeepsVisibleCardsWhenAuthorIsMissingAndLoadsSharedParentsOnce() {
    var linked =
        List.of(
            connection(1L, ConnectionBlockType.POST, 10L),
            connection(2L, ConnectionBlockType.HIGHLIGHT, 20L),
            connection(3L, ConnectionBlockType.NOTE, 40L),
            connection(4L, ConnectionBlockType.HIGHLIGHT, 21L),
            connection(5L, ConnectionBlockType.POST, 404L));
    when(highlights.findAllByIdIn(List.of(20L, 21L)))
        .thenReturn(List.of(highlight(20L, 10L), highlight(21L, 30L)));
    when(noteBodies.blocksByIds(List.of(40L)))
        .thenReturn(Map.of(40L, new NoteBlock(40L, "A note remains visible", "note_author")));
    when(posts.findAllByIdIn(Set.of(10L, 30L, 404L)))
        .thenReturn(List.of(post(10L, true), post(30L, false)));
    when(users.findAllByIdIn(anyCollection())).thenReturn(List.of());

    var cards = reader.connections(linked);

    assertThat(cards).extracting(ConnectionView::id).containsExactly(1L, 2L, 3L);
    assertThat(cards)
        .extracting(ConnectionView::blockType)
        .containsExactly("POST", "HIGHLIGHT", "NOTE");
    assertThat(cards.get(0).username()).isNull();
    assertThat(cards.get(1).username()).isNull();
    assertThat(cards.get(1).quote()).isEqualTo("Quoted post 10");
    assertThat(cards.get(2).body()).isEqualTo("A note remains visible");
    assertThat(cards.get(2).username()).isEqualTo("note_author");
    assertThat(cards.get(2).noteId()).isEqualTo(40L);
    verify(posts).findAllByIdIn(Set.of(10L, 30L, 404L));
    verifyNoMoreInteractions(posts);
    verify(users).findAllByIdIn(Set.of(7L));
  }

  private static CollectionConnectionEntity connection(
      long id, ConnectionBlockType type, long refId) {
    return connection(id, type, refId, (int) id);
  }

  private static CollectionConnectionEntity connection(
      long id, ConnectionBlockType type, long refId, int position) {
    var connection = new CollectionConnectionEntity(1L, type, refId, "Worth reading", position);
    ReflectionTestUtils.setField(connection, "id", id);
    return connection;
  }

  private static CollectionConnectionEntity postIn(long collectionId, long refId, int position) {
    var connection =
        new CollectionConnectionEntity(
            collectionId, ConnectionBlockType.POST, refId, "Worth reading", position);
    ReflectionTestUtils.setField(connection, "id", collectionId * 100 + position);
    return connection;
  }

  private static CollectionEntity collection(long id, boolean ordered) {
    var collection =
        new CollectionEntity(7L, "Collection " + id, null, CollectionVisibility.PUBLIC, ordered);
    ReflectionTestUtils.setField(collection, "id", id);
    return collection;
  }

  private static PostEntity post(long id, boolean published) {
    var post = new PostEntity(7L, "post-" + id, "Post " + id, "ko");
    ReflectionTestUtils.setField(post, "id", id);
    if (published) post.publish();
    return post;
  }

  private static PostHighlightEntity highlight(long id, long postId) {
    var highlight = new PostHighlightEntity(postId, 7L, 0, 0, 0, 5, "Quoted post " + postId, null);
    ReflectionTestUtils.setField(highlight, "id", id);
    return highlight;
  }
}
