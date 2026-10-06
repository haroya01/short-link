package com.example.short_link.note.domain.repository;

import com.example.short_link.common.link.LinkPreviewReader.Preview;
import com.example.short_link.note.domain.NoteLinkPreviewEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NoteLinkPreviewRepository {

  List<NoteLinkPreviewEntity> findByNoteIds(Collection<Long> noteIds);

  void put(Long noteId, Preview preview, Instant fetchedAt);

  void remove(Long noteId);
}
