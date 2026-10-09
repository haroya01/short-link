package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesNote;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SeriesItemReader {

  Map<Long, SeriesNote> notes(Collection<Long> noteIds);

  // What a reader can open, in series order: published posts and public or unlisted notes.
  List<SeriesEntry> readableEntries(Long seriesId);
}
