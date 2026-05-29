package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

  private final JpaPostRepository jpa;

  @Override
  public Optional<PostEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<PostEntity> findByUserIdAndSlug(Long userId, String slug) {
    return jpa.findByUserIdAndSlug(userId, slug);
  }

  @Override
  public PostEntity save(PostEntity post) {
    return jpa.save(post);
  }

  @Override
  public void delete(PostEntity post) {
    jpa.delete(post);
  }

  @Override
  public boolean existsByUserIdAndSlug(Long userId, String slug) {
    return jpa.existsByUserIdAndSlug(userId, slug);
  }

  @Override
  public List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<PostEntity> findAllByUserIdAndStatusOrderByPublishedAtDesc(
      Long userId, PostStatus status) {
    return jpa.findAllByUserIdAndStatusOrderByPublishedAtDesc(userId, status);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdOrderBySeriesOrderAsc(Long seriesId) {
    return jpa.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
  }

  @Override
  public List<PostEntity> findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
      Long seriesId, PostStatus status) {
    return jpa.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(seriesId, status);
  }

  @Override
  public List<PostEntity> findPublishedRecent(int page, int size) {
    return jpa.findByStatusOrderByPublishedAtDesc(PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> findPublishedTrending(int page, int size) {
    return jpa.findByStatusOrderByViewCountDescPublishedAtDesc(
        PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countPublished() {
    return jpa.countByStatus(PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findPublishedByTag(String tag, int page, int size) {
    return jpa.findPublishedByTag(tag, PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByTag(String tag) {
    return jpa.countPublishedByTag(tag, PostStatus.PUBLISHED);
  }

  @Override
  public List<PostEntity> findPublishedByAuthorIds(Collection<Long> authorIds, int page, int size) {
    return jpa.findPublishedByAuthorIds(
        authorIds, PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public long countPublishedByAuthorIds(Collection<Long> authorIds) {
    return jpa.countPublishedByAuthorIds(authorIds, PostStatus.PUBLISHED);
  }
}
