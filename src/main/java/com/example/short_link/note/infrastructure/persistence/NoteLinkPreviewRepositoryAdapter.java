package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.common.link.LinkPreviewReader.Preview;
import com.example.short_link.note.domain.NoteLinkPreviewEntity;
import com.example.short_link.note.domain.repository.NoteLinkPreviewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class NoteLinkPreviewRepositoryAdapter implements NoteLinkPreviewRepository {

  private final JpaNoteLinkPreviewRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public List<NoteLinkPreviewEntity> findByNoteIds(Collection<Long> noteIds) {
    return noteIds.isEmpty() ? List.of() : jpa.findByNoteIdIn(noteIds);
  }

  // One statement whether the note had a card or not; a note deleted meanwhile fails the FK and
  // is simply skipped by the caller.
  @Override
  @Transactional
  public void put(Long noteId, Preview preview, Instant fetchedAt) {
    em.createNativeQuery(
            "INSERT INTO note_link_preview (note_id, url, title, description, image_url, fetched_at)"
                + " VALUES (:noteId, :url, :title, :description, :image, :fetchedAt) AS fresh"
                + " ON DUPLICATE KEY UPDATE url = fresh.url, title = fresh.title,"
                + " description = fresh.description, image_url = fresh.image_url,"
                + " fetched_at = fresh.fetched_at")
        .setParameter("noteId", noteId)
        .setParameter("url", preview.url())
        .setParameter("title", preview.title())
        .setParameter("description", preview.description())
        .setParameter("image", preview.image())
        .setParameter("fetchedAt", fetchedAt)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void remove(Long noteId) {
    em.createNativeQuery("DELETE FROM note_link_preview WHERE note_id = :noteId")
        .setParameter("noteId", noteId)
        .executeUpdate();
  }
}
