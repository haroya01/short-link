package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.SeriesActivity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesEntry;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.domain.repository.SeriesSubscriptionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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

  private static final int MIN_ITEMS = 2;

  private static final int PREVIEW_POSTS = 4;

  private final UserRepository userRepository;
  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;
  private final SeriesSubscriptionRepository subscriptionRepository;
  private final SeriesItemRepository seriesItemRepository;
  private final SeriesItemReader seriesItemReader;

  public List<PublicSeriesCard> subscribedSeries(Long userId) {
    List<Long> ids = subscriptionRepository.findSubscribedSeriesIds(userId);
    if (ids.isEmpty()) return List.of();

    Map<Long, SeriesEntity> series =
        seriesRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(SeriesEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors = liveAuthorsOf(series.values());
    Map<Long, List<SeriesEntry>> entries = seriesItemReader.readableEntries(ids);

    return ids.stream()
        .map(series::get)
        .filter(Objects::nonNull)
        .filter(s -> authors.containsKey(s.getUserId()))
        .filter(s -> entries.containsKey(s.getId()))
        .map(s -> card(s, authors.get(s.getUserId()), entries.get(s.getId())))
        .sorted(
            Comparator.comparing(
                PublicSeriesCard::lastPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  // 삭제 작성자를 제외해도 요청 수를 채울 수 있도록 후보를 더 조회한다.
  public List<PublicSeriesCard> discoverSeries(Long viewerId, int limit) {
    int safeLimit = Math.max(limit, 1);
    List<SeriesActivity> ranked = seriesItemReader.activeSeries(viewerId, MIN_ITEMS, safeLimit * 2);
    if (ranked.isEmpty()) return List.of();

    Map<Long, SeriesEntity> series =
        seriesRepository
            .findAllByIdIn(ranked.stream().map(SeriesActivity::seriesId).toList())
            .stream()
            .collect(Collectors.toMap(SeriesEntity::getId, Function.identity()));
    Map<Long, UserEntity> authors = liveAuthorsOf(series.values());

    // 제외될 시리즈의 항목을 읽지 않도록 작성자 검사와 개수 제한을 먼저 적용한다.
    List<SeriesEntity> chosen =
        ranked.stream()
            .map(a -> series.get(a.seriesId()))
            .filter(Objects::nonNull)
            .filter(s -> authors.containsKey(s.getUserId()))
            .limit(safeLimit)
            .toList();
    Map<Long, List<SeriesEntry>> entries =
        seriesItemReader.readableEntries(chosen.stream().map(SeriesEntity::getId).toList());
    return chosen.stream()
        .filter(s -> entries.containsKey(s.getId()))
        .map(s -> card(s, authors.get(s.getUserId()), entries.get(s.getId())))
        .toList();
  }

  private Map<Long, UserEntity> liveAuthorsOf(Collection<SeriesEntity> series) {
    return userRepository
        .findAllByIdIn(series.stream().map(SeriesEntity::getUserId).distinct().toList())
        .stream()
        .filter(u -> !u.isDeleted())
        .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
  }

  private static PublicSeriesCard card(
      SeriesEntity series, UserEntity author, List<SeriesEntry> entries) {
    List<SeriesEntry> posts =
        entries.stream().filter(e -> e.type() == SeriesItemType.POST).toList();
    Instant last =
        entries.stream()
            .map(SeriesEntry::at)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    return new PublicSeriesCard(
        series.getId(),
        PublicAuthorView.from(author),
        series.getSlug(),
        series.getTitle(),
        posts.size(),
        last,
        posts.stream()
            .limit(PREVIEW_POSTS)
            .map(p -> new SeriesPostRef(p.slug(), p.title(), p.ogImageUrl()))
            .toList(),
        entries.size(),
        entries.stream().limit(PREVIEW_POSTS).map(SeriesItemPreview::of).toList());
  }

  public PublicSeriesListView listPublicSeries(String username) {
    UserEntity author = resolveAuthor(username);
    List<SeriesEntity> all = seriesRepository.findAllByUserIdOrderByCreatedAtDesc(author.getId());
    List<Long> ids = all.stream().map(SeriesEntity::getId).toList();
    Map<Long, List<PostEntity>> publishedBySeries =
        postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(ids).stream()
            .filter(p -> p.getStatus() == PostStatus.PUBLISHED)
            .collect(Collectors.groupingBy(PostEntity::getSeriesId));
    List<SeriesItemEntity> rows = seriesItemRepository.findBySeriesIdIn(ids);
    Map<Long, SeriesNote> notes = seriesItemReader.notes(noteIds(rows));
    Map<Long, Long> sharedNotes =
        rows.stream()
            .filter(r -> r.getType() == SeriesItemType.NOTE)
            .filter(r -> notes.containsKey(r.getRefId()) && notes.get(r.getRefId()).shared())
            .collect(Collectors.groupingBy(SeriesItemEntity::getSeriesId, Collectors.counting()));
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
                      published.size() + sharedNotes.getOrDefault(s.getId(), 0L).intValue(),
                      distinctTags(published));
                })
            .filter(s -> s.itemCount() > 0)
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
    Map<Long, PostEntity> published =
        members.stream().collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    List<SeriesItemEntity> rows = seriesItemRepository.findBySeriesId(series.getId());
    Map<Long, SeriesNote> notes = seriesItemReader.notes(noteIds(rows));
    List<PublicSeriesItem> items = new ArrayList<>(rows.size());
    for (SeriesItemEntity row : rows) {
      PostEntity post = row.getType() == SeriesItemType.POST ? published.get(row.getRefId()) : null;
      SeriesNote note = row.getType() == SeriesItemType.NOTE ? notes.get(row.getRefId()) : null;
      if (post != null) {
        items.add(new PublicSeriesItem(row.getType().name(), PublicPostListItem.from(post), null));
      } else if (note != null && note.shared()) {
        items.add(new PublicSeriesItem(row.getType().name(), null, SeriesNoteView.from(note)));
      }
    }
    return new PublicSeriesDetail(
        PublicAuthorView.from(author),
        new PublicSeriesListItem(
            series.getId(),
            series.getSlug(),
            series.getTitle(),
            members.size(),
            items.size(),
            distinctTags(members)),
        members.stream().map(PublicPostListItem::from).toList(),
        items);
  }

  private static List<Long> noteIds(List<SeriesItemEntity> rows) {
    return rows.stream()
        .filter(r -> r.getType() == SeriesItemType.NOTE)
        .map(SeriesItemEntity::getRefId)
        .toList();
  }

  private UserEntity resolveAuthor(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    return userRepository
        .findByUsername(normalized)
        .filter(u -> !u.isDeleted())
        .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, normalized));
  }
}
