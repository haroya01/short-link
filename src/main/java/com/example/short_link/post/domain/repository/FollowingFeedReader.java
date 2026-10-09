package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.FollowingFeedRef;
import java.util.Collection;
import java.util.List;

// The subscription feed as one stream, newest first: published posts by followed authors, in
// subscribed series or under followed topics, and the public or unlisted notes of subscribed
// series.
public interface FollowingFeedReader {

  List<FollowingFeedRef> page(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags,
      int offset,
      int limit);

  long count(
      Long viewerId,
      Collection<Long> authorIds,
      Collection<Long> seriesIds,
      Collection<String> tags);
}
