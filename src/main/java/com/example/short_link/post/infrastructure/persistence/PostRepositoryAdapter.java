package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.AuthorPostStats;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.TagCount;
import com.example.short_link.post.domain.repository.PostRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostRepositoryAdapter implements PostRepository {

  // Rolling window the trending feed ranks views over — "recent traction" = the last 7 days.
  private static final Duration TRENDING_WINDOW = Duration.ofDays(7);

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
    Instant since = Instant.now().minus(TRENDING_WINDOW);
    return jpa.findPublishedTrendingSince(since, PageRequest.of(page, size));
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
  public List<PostEntity> searchPublished(String query, int page, int size) {
    return jpa.searchPublished(
        likePattern(query), PostStatus.PUBLISHED, PageRequest.of(page, size));
  }

  @Override
  public List<PostEntity> searchPublishedTrending(String query, int page, int size) {
    Instant since = Instant.now().minus(TRENDING_WINDOW);
    return jpa.searchPublishedTrendingSince(likePattern(query), since, PageRequest.of(page, size));
  }

  @Override
  public long countSearchPublished(String query) {
    return jpa.countSearchPublished(likePattern(query), PostStatus.PUBLISHED);
  }

  // Lowercase + escape the LIKE metacharacters in user input, then wrap in %…% for a contains
  // match.
  // '!' is the escape char declared in the queries; escape it first so a literal '!' can't shield
  // the
  // following char. Without this, a search for "50%" would match every title.
  private static String likePattern(String query) {
    String escaped = query.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
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

  @Override
  public List<TagCount> findPopularTags(int limit) {
    return jpa.findPopularTags(PostStatus.PUBLISHED, PageRequest.of(0, limit)).stream()
        .map(row -> new TagCount((String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  @Override
  public List<AuthorPostStats> findTopAuthorStats(int limit) {
    return jpa.findTopAuthorIds(PostStatus.PUBLISHED, PageRequest.of(0, limit)).stream()
        .map(
            row ->
                new AuthorPostStats(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue()))
        .toList();
  }
}
