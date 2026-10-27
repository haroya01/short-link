package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.TrendingLink;
import com.example.short_link.note.domain.TrendingTag;
import java.time.Instant;
import java.util.List;

public interface NoteTrendsReader {

  List<NoteEntity> trending(Long viewerId, int offset, int limit);

  List<TrendingTag> trendingTags(Instant now, int days, int minAccounts, int limit);

  List<TrendingLink> trendingLinks(Instant now, int days, int minAccounts, int limit);
}
