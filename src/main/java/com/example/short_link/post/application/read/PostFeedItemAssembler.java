package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesSummary;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Batch-loads authors and series and drops deleted-author posts, preserving the input order. */
@Component
@RequiredArgsConstructor
public class PostFeedItemAssembler {

  private final UserRepository userRepository;
  private final SeriesRepository seriesRepository;

  public List<PublicFeedItem> assemble(List<PostEntity> posts) {
    List<Long> authorIds = posts.stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserEntity> authors =
        userRepository.findAllByIdIn(authorIds).stream()
            .filter(u -> !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    List<PostEntity> visible =
        posts.stream().filter(p -> authors.containsKey(p.getUserId())).toList();
    Map<Long, FeedSeriesRef> series = seriesOf(visible);
    return visible.stream()
        .map(
            p -> {
              PublicFeedItem item = toItem(p, authors.get(p.getUserId()));
              FeedSeriesRef ref = p.getSeriesId() == null ? null : series.get(p.getSeriesId());
              return ref == null ? item : item.withSeries(ref);
            })
        .toList();
  }

  private Map<Long, FeedSeriesRef> seriesOf(List<PostEntity> posts) {
    List<Long> seriesIds =
        posts.stream().map(PostEntity::getSeriesId).filter(Objects::nonNull).distinct().toList();
    if (seriesIds.isEmpty()) return Map.of();
    return seriesRepository.findPublishedSummaries(seriesIds).stream()
        .collect(Collectors.toMap(SeriesSummary::seriesId, FeedSeriesRef::from));
  }

  public PublicFeedItem toItem(PostEntity post, UserEntity author) {
    return new PublicFeedItem(
        post.getId(),
        PublicAuthorView.from(author),
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getPublishedAt(),
        post.getViewCount(),
        post.getLikeCount());
  }
}
