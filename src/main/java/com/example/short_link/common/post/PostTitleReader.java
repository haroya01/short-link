package com.example.short_link.common.post;

import java.util.Collection;
import java.util.Map;

// The post slice implements this port to avoid a dependency cycle with link-click analytics. A
// title comes back only for a post whose card anyone may see, since a link's stats can be public.
public interface PostTitleReader {

  Map<Long, String> findTitlesByIds(Collection<Long> postIds);
}
