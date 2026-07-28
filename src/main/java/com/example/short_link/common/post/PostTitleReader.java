package com.example.short_link.common.post;

import java.util.Collection;
import java.util.Map;

/**
 * Neutral read port resolving post ids to their titles for other slices. The link slice attributes
 * clicks to the post that embedded the link ({@code click_event.post_id}) and needs a readable
 * label for that row — a link→post type dependency would close a cycle in the slice graph
 * (ArchUnit-enforced), so it goes through here instead. Mirrors {@code
 * common.post.PublishedPostCountReader}; the post slice provides the implementation.
 */
public interface PostTitleReader {

  /**
   * Titles by post id, batched into one query. Ids with no surviving post (deleted after the click
   * was recorded) are simply absent from the map — callers keep the count and show no title rather
   * than dropping the row.
   */
  Map<Long, String> findTitlesByIds(Collection<Long> postIds);
}
