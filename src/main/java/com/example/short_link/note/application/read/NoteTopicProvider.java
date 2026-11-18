package com.example.short_link.note.application.read;

import com.example.short_link.common.note.Hashtags;
import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.note.domain.repository.NoteFeedReader;
import com.example.short_link.note.domain.repository.NoteTrendsReader;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class NoteTopicProvider implements NoteTopicReader {

  private final NoteTrendsReader trends;
  private final NoteFeedReader feeds;

  @Override
  @Transactional(readOnly = true)
  public List<Tag> trendingTags(Instant now, int days, int minAccounts, int limit) {
    return trends.trendingTags(now, days, minAccounts, limit).stream()
        .map(tag -> new Tag(tag.tag(), tag.accounts()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Link> trendingLinks(Instant now, int days, int minAccounts, int limit) {
    return trends.trendingLinks(now, days, minAccounts, limit).stream()
        .map(link -> new Link(link.url(), link.title(), link.imageUrl(), link.accounts()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Kept> kept(Carrying what, Long viewerId, Long beforeId, int limit) {
    if (!asked(what)) {
      return List.of();
    }
    return what.tag() != null
        ? feeds.taggedBefore(what.tag(), viewerId, beforeId, limit)
        : feeds.linkedBefore(what.url(), viewerId, beforeId, limit);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> keptAmong(Carrying what, Long viewerId, Collection<String> uris) {
    if (!asked(what) || uris.isEmpty()) {
      return Set.of();
    }
    return Set.copyOf(
        what.tag() != null
            ? feeds.taggedAmong(what.tag(), viewerId, uris)
            : feeds.linkedAmong(what.url(), viewerId, uris));
  }

  private static boolean asked(Carrying what) {
    return what.tag() != null
        ? !what.tag().isBlank() && what.tag().length() <= Hashtags.MAX_LENGTH
        : what.url() != null && !what.url().isBlank();
  }
}
