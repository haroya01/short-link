package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesFeedNote;
import com.example.short_link.post.domain.SeriesNote;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SeriesItemReader {

  Map<Long, SeriesNote> notes(Collection<Long> noteIds);

  // Readable notes that sit in a series, with their live author and their series, in one read.
  Map<Long, SeriesFeedNote> feedNotes(Collection<Long> noteIds);

  // What a reader can open, in series order: published posts and public or unlisted notes.
  List<SeriesEntry> readableEntries(Long seriesId);

  Map<Long, List<SeriesEntry>> readableEntries(Collection<Long> seriesIds);

  // Series with at least minItems readable items, the most recently active first. Posts count only
  // past the discovery floor; notes count when anyone may read them.
  List<SeriesActivity> activeSeries(int minItems, int limit);
}
