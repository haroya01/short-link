package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.repository.RemoteMediaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class RemoteMediaRepositoryAdapter implements RemoteMediaRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public boolean kept(String key) {
    return !em.createNativeQuery("SELECT 1 FROM remote_media WHERE storage_key = :key")
        .setParameter("key", key)
        .getResultList()
        .isEmpty();
  }

  @Override
  @Transactional
  public int attach(
      String key, String remoteUrl, String copyUrl, Collection<Long> mediaIds, Instant cachedAt) {
    int shown =
        em.createNativeQuery(
                "UPDATE note_media SET url = :copy, storage_key = :key, remote_url = :remote"
                    + " WHERE id IN (:ids) AND url = :remote AND storage_key = ''")
            .setParameter("copy", copyUrl)
            .setParameter("key", key)
            .setParameter("remote", remoteUrl)
            .setParameter("ids", mediaIds)
            .executeUpdate();
    if (shown > 0) {
      em.createNativeQuery(
              "INSERT IGNORE INTO remote_media (storage_key, remote_url, cached_at)"
                  + " VALUES (:key, :remote, :at)")
          .setParameter("key", key)
          .setParameter("remote", remoteUrl)
          .setParameter("at", cachedAt)
          .executeUpdate();
    }
    return shown;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> expired(Instant cachedBefore, int limit) {
    return em.createNativeQuery(
            "SELECT storage_key FROM remote_media WHERE cached_at < :before"
                + " ORDER BY cached_at LIMIT :limit")
        .setParameter("before", cachedBefore)
        .setParameter("limit", limit)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> orphaned(int limit) {
    return em.createNativeQuery(
            "SELECT r.storage_key FROM remote_media r WHERE NOT EXISTS"
                + " (SELECT 1 FROM note_media m WHERE m.storage_key = r.storage_key)"
                + " LIMIT :limit")
        .setParameter("limit", limit)
        .getResultList();
  }

  @Override
  @Transactional
  public int detach(String key) {
    return em.createNativeQuery(
            "UPDATE note_media SET url = remote_url, storage_key = '', remote_url = NULL"
                + " WHERE storage_key = :key AND remote_url IS NOT NULL")
        .setParameter("key", key)
        .executeUpdate();
  }

  @Override
  @Transactional
  public void forget(String key) {
    em.createNativeQuery("DELETE FROM remote_media WHERE storage_key = :key")
        .setParameter("key", key)
        .executeUpdate();
  }
}
