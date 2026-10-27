package com.example.short_link.post.application.read;

import com.example.short_link.post.application.access.PostAccess;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesSummary;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostFeedItemAssembler {

  private final UserSummaryReader userSummaries;
  private final SeriesRepository seriesRepository;
  private final PostRooms rooms;

  public List<PublicFeedItem> assemble(List<PostEntity> posts) {
    List<Long> authorIds = posts.stream().map(PostEntity::getUserId).distinct().toList();
    Map<Long, UserSummary> authors =
        userSummaries.findAllActive(authorIds).stream()
            .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    List<PostEntity> visible =
        posts.stream().filter(p -> authors.containsKey(p.getUserId())).toList();
    Map<Long, FeedSeriesRef> series = seriesOf(visible);
    Map<Long, PostRoomView> roomOf = rooms.of(visible);
    return visible.stream()
        .map(
            p -> {
              PublicFeedItem item = toItem(p, authors.get(p.getUserId()));
              FeedSeriesRef ref = p.getSeriesId() == null ? null : series.get(p.getSeriesId());
              PostRoomView room = roomOf.get(p.getId());
              item = room == null ? item : item.in(room);
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

  public PublicFeedItem toItem(PostEntity post, UserSummary author) {
    PostAccess card = PostAccessPolicy.card(post);
    boolean redacted = card.redacted();
    return new PublicFeedItem(
        post.getId(),
        PublicAuthorView.from(author),
        post.getSlug(),
        post.getTitle(),
        redacted ? null : post.getExcerpt(),
        redacted ? null : post.getOgImageUrl(),
        redacted ? null : post.thumbnailUrl(),
        post.getLanguageTag(),
        redacted ? List.of() : List.copyOf(post.getTags()),
        post.getPublishedAt(),
        redacted ? 0 : post.getViewCount(),
        redacted ? 0 : post.getLikeCount(),
        null,
        null,
        post.getAudience().name(),
        post.isRegionRestricted() ? Boolean.TRUE : null,
        PostLockView.of(card));
  }
}
