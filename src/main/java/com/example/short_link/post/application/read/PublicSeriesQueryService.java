package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, unauthenticated series read. Only PUBLISHED member posts are exposed. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicSeriesQueryService {

  // A "series" worth showcasing has at least two published posts; a lone post isn't a series yet.
  private static final int MIN_POSTS = 2;

  // How many member titles the discovery card previews — enough to show "what's inside" at a
  // glance.
  private static final int PREVIEW_POSTS = 4;

  private final UserRepository userRepository;
  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;

  /**
   * Cross-author series for the discovery feed — most recently active first. Hydrates each ranked
   * series with its author, dropping any whose author is deleted/missing, and keeps the activity
   * ordering. Over-fetches a little so deleted-author drops don't shrink the result below {@code
   * limit}.
   */
  public List<PublicSeriesCard> discoverSeries(int limit) {
    int safeLimit = Math.max(limit, 1);
    List<SeriesActivity> ranked = postRepository.findActiveSeries(MIN_POSTS, safeLimit * 2);
    if (ranked.isEmpty()) return List.of();

    Map<Long, SeriesEntity> series =
        seriesRepository
            .findAllByIdIn(ranked.stream().map(SeriesActivity::seriesId).toList())
            .stream()
            .collect(Collectors.toMap(SeriesEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors =
        userRepository
            .findAllByIdIn(
                series.values().stream().map(SeriesEntity::getUserId).distinct().toList())
            .stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    // Resolve + drop missing/deleted authors and cut to the limit FIRST, then fetch member previews
    // only for the survivors (one small query each) — so dropped/over-limit series cost nothing
    // extra.
    return ranked.stream()
        .map(a -> resolve(a, series.get(a.seriesId()), authors))
        .filter(Objects::nonNull)
        .limit(safeLimit)
        .map(this::toCard)
        .toList();
  }

  private Resolved resolve(
      SeriesActivity activity, SeriesEntity series, Map<Long, UserEntity> authors) {
    if (series == null) return null;
    UserEntity author = authors.get(series.getUserId());
    if (author == null) return null; // deleted/missing author → drop
    return new Resolved(activity, series, author);
  }

  private PublicSeriesCard toCard(Resolved r) {
    return new PublicSeriesCard(
        r.series().getId(),
        PublicAuthorView.from(r.author()),
        r.series().getSlug(),
        r.series().getTitle(),
        (int) r.activity().postCount(),
        r.activity().lastPublishedAt(),
        memberPreviews(r.series().getId()));
  }

  /** The first few published members, in series order — the card's "what's inside" preview. */
  private List<SeriesPostRef> memberPreviews(Long seriesId) {
    return postRepository
        .findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(seriesId, PostStatus.PUBLISHED)
        .stream()
        .limit(PREVIEW_POSTS)
        .map(p -> new SeriesPostRef(p.getSlug(), p.getTitle()))
        .toList();
  }

  private record Resolved(SeriesActivity activity, SeriesEntity series, UserEntity author) {}

  public PublicSeriesListView listPublicSeries(String username) {
    UserEntity author = resolveAuthor(username);
    List<PublicSeriesListItem> series =
        seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId()).stream()
            .map(
                s ->
                    new PublicSeriesListItem(
                        s.getId(), s.getSlug(), s.getTitle(), publishedCount(s.getId())))
            .filter(s -> s.postCount() > 0)
            .toList();
    return new PublicSeriesListView(PublicAuthorView.from(author), series);
  }

  public PublicSeriesDetail findPublicSeries(String username, String slug) {
    UserEntity author = resolveAuthor(username);
    SeriesEntity series =
        seriesRepository
            .findByUserIdAndSlug(author.getId(), slug)
            .orElseThrow(() -> new PostException(PostErrorCode.SERIES_NOT_FOUND, slug));
    List<PublicPostListItem> posts =
        postRepository
            .findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(series.getId(), PostStatus.PUBLISHED)
            .stream()
            .map(PublicPostListItem::from)
            .toList();
    return new PublicSeriesDetail(
        PublicAuthorView.from(author),
        new PublicSeriesListItem(series.getId(), series.getSlug(), series.getTitle(), posts.size()),
        posts);
  }

  private int publishedCount(Long seriesId) {
    return postRepository
        .findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(seriesId, PostStatus.PUBLISHED)
        .size();
  }

  private UserEntity resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase();
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
