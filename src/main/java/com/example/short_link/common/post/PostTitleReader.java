package com.example.short_link.common.post;

import java.util.Collection;
import java.util.Map;

/** The post slice implements this port to avoid a dependency cycle with link-click analytics. */
public interface PostTitleReader {

  /**
   * Batched titles by post id. Deleted posts are absent from the map; callers retain their click
   * counts without a title.
   */
  Map<Long, String> findTitlesByIds(Collection<Long> postIds);
}
