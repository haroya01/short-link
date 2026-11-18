package com.example.short_link.common.note;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface NoteTopicReader {

  record Tag(String name, long accounts) {}

  record Link(String url, String title, String image, long accounts) {}

  record Carrying(String tag, String url) {}

  record Kept(Long id, String uri, Instant createdAt) {}

  List<Tag> trendingTags(Instant now, int days, int minAccounts, int limit);

  List<Link> trendingLinks(Instant now, int days, int minAccounts, int limit);

  List<Kept> kept(Carrying what, Long viewerId, Long beforeId, int limit);

  Set<String> keptAmong(Carrying what, Long viewerId, Collection<String> uris);
}
