package com.example.short_link.post.collection.infrastructure;

import com.example.short_link.common.collection.CollectionConnectionCleaner;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Collection-slice implementation of the neutral cleanup port. A single bulk delete per block type;
 * an empty highlight set is skipped so a post with no highlights issues no query.
 */
@Repository
@RequiredArgsConstructor
class CollectionConnectionCleanerAdapter implements CollectionConnectionCleaner {

  private final JpaCollectionConnectionRepository jpa;

  @Override
  public void purgeForPost(long postId) {
    jpa.deleteByBlockTypeAndRefIdIn(ConnectionBlockType.POST, List.of(postId));
  }

  @Override
  public void purgeForHighlights(Collection<Long> highlightIds) {
    if (highlightIds.isEmpty()) return;
    jpa.deleteByBlockTypeAndRefIdIn(ConnectionBlockType.HIGHLIGHT, highlightIds);
  }

  @Override
  public void purgeForNote(long noteId) {
    jpa.deleteByBlockTypeAndRefIdIn(ConnectionBlockType.NOTE, List.of(noteId));
  }
}
