package com.example.short_link.post.collection.infrastructure;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionConnectionCleanerAdapterTest {

  @Mock private JpaCollectionConnectionRepository jpa;
  @InjectMocks private CollectionConnectionCleanerAdapter adapter;

  @Test
  void purgeForPostDeletesByPostRef() {
    adapter.purgeForPost(5L);

    verify(jpa).deleteByBlockTypeAndRefIdIn(ConnectionBlockType.POST, List.of(5L));
  }

  @Test
  void purgeForNoteDeletesByNoteRef() {
    adapter.purgeForNote(9L);

    verify(jpa).deleteByBlockTypeAndRefIdIn(ConnectionBlockType.NOTE, List.of(9L));
  }

  @Test
  void purgeForHighlightsDeletesByHighlightRefs() {
    adapter.purgeForHighlights(List.of(1L, 2L));

    verify(jpa).deleteByBlockTypeAndRefIdIn(ConnectionBlockType.HIGHLIGHT, List.of(1L, 2L));
  }

  @Test
  void purgeForHighlightsSkipsEmpty() {
    adapter.purgeForHighlights(List.of());

    verifyNoInteractions(jpa);
  }
}
