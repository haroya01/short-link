package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicSeriesQueryService {

  private static final int MIN_POSTS = 2;

  private static final int PREVIEW_POSTS = 4;

  private final UserRepository userRepository;
  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;
  private final SeriesSubscriptionRepository subscriptionRepository;

  /** 구독 시리즈를 최근 활동순으로 반환한다. 삭제 작성자와 발행 글이 없는 시리즈는 제외한다. */
  public List<PublicSeriesCard> subscribedSeries(Long userId) {
    List<Long> ids = subscriptionRepository.findSubscribedSeriesIds(userId);
    if (ids.isEmpty()) return List.of();

    Map<Long, SeriesEntity> series =
        seriesRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(SeriesEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors =
        userRepository
            .findAllByIdIn(
                series.values().stream().map(SeriesEntity::getUserId).distinct().toList())
            .stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

    return ids.stream()
        .map(series::get)
        .filter(Objects::nonNull)
        .filter(s -> authors.containsKey(s.getUserId()))
        .map(s -> subscribedCard(s, authors.get(s.getUserId())))
        .filter(Objects::nonNull)
        .sorted(
            Comparator.comparing(
                PublicSeriesCard::lastPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  private PublicSeriesCard subscribedCard(SeriesEntity s, UserEntity author) {
    List<PostEntity> published =
        postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            s.getId(), PostStatus.PUBLISHED);
    if (published.isEmpty()) return null;
    Instant last =
        published.stream()
            .map(PostEntity::getPublishedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    List<SeriesPostRef> previews =
        published.stream()
            .limit(PREVIEW_POSTS)
            .map(p -> new SeriesPostRef(p.getSlug(), p.getTitle(), p.getOgImageUrl()))
            .toList();
    return new PublicSeriesCard(
        s.getId(),
        PublicAuthorView.from(author),
        s.getSlug(),
        s.getTitle(),
        published.size(),
        last,
        previews);
  }

  /** 삭제 작성자를 제외해도 요청 수를 채울 수 있도록 후보를 더 조회한다. */
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

    // 제외될 시리즈의 미리보기를 조회하지 않도록 작성자 검사와 개수 제한을 먼저 적용한다.
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
    if (author == null) return null;
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

  private List<SeriesPostRef> memberPreviews(Long seriesId) {
    return postRepository
        .findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(seriesId, PostStatus.PUBLISHED)
        .stream()
        .limit(PREVIEW_POSTS)
        .map(p -> new SeriesPostRef(p.getSlug(), p.getTitle(), p.getOgImageUrl()))
        .toList();
  }

  private record Resolved(SeriesActivity activity, SeriesEntity series, UserEntity author) {}

  public PublicSeriesListView listPublicSeries(String username) {
    UserEntity author = resolveAuthor(username);
    List<SeriesEntity> all = seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId());
    Map<Long, List<PostEntity>> publishedBySeries =
        postRepository
            .findAllBySeriesIdInOrderBySeriesOrderAsc(
                all.stream().map(SeriesEntity::getId).toList())
            .stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .collect(Collectors.groupingBy(PostEntity::getSeriesId));
    List<PublicSeriesListItem> series =
        all.stream()
            .map(
                s -> {
                  List<PostEntity> published = publishedBySeries.getOrDefault(s.getId(), List.of());
                  return new PublicSeriesListItem(
                      s.getId(),
                      s.getSlug(),
                      s.getTitle(),
                      published.size(),
                      distinctTags(published));
                })
            .filter(s -> s.postCount() > 0)
            .toList();
    return new PublicSeriesListView(PublicAuthorView.from(author), series);
  }

  private static List<String> distinctTags(List<PostEntity> posts) {
    return posts.stream().flatMap(p -> p.getTags().stream()).distinct().toList();
  }

  public PublicSeriesDetail findPublicSeries(String username, String slug) {
    UserEntity author = resolveAuthor(username);
    SeriesEntity series =
        seriesRepository
            .findByUserIdAndSlug(author.getId(), slug)
            .orElseThrow(() -> new PostException(PostErrorCode.SERIES_NOT_FOUND, slug));
    List<PostEntity> members =
        postRepository.findAllBySeriesIdAndStatusOrderBySeriesOrderAsc(
            series.getId(), PostStatus.PUBLISHED);
    List<PublicPostListItem> posts = members.stream().map(PublicPostListItem::from).toList();
    return new PublicSeriesDetail(
        PublicAuthorView.from(author),
        new PublicSeriesListItem(
            series.getId(),
            series.getSlug(),
            series.getTitle(),
            posts.size(),
            distinctTags(members)),
        posts);
  }

  private UserEntity resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
