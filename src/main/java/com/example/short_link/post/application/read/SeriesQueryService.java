package com.example.short_link.post.application.read;

import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.SeriesItemEntity;
import com.example.short_link.post.domain.SeriesItemType;
import com.example.short_link.post.domain.SeriesNote;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesItemReader;
import com.example.short_link.post.domain.repository.SeriesItemRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesQueryService {

  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;
  private final SeriesItemRepository seriesItemRepository;
  private final SeriesItemReader seriesItemReader;
  private final SeriesOwnership seriesOwnership;

  public List<SeriesView> listMine(Long userId) {
    List<SeriesEntity> all = seriesRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    List<Long> ids = all.stream().map(SeriesEntity::getId).toList();
    if (ids.isEmpty()) return List.of();
    Map<Long, Long> posts =
        postRepository.findAllBySeriesIdInOrderBySeriesOrderAsc(ids).stream()
            .collect(Collectors.groupingBy(PostEntity::getSeriesId, Collectors.counting()));
    Map<Long, Long> items =
        seriesItemRepository.findBySeriesIdIn(ids).stream()
            .collect(Collectors.groupingBy(SeriesItemEntity::getSeriesId, Collectors.counting()));
    return all.stream()
        .map(
            s ->
                SeriesView.from(
                    s,
                    posts.getOrDefault(s.getId(), 0L).intValue(),
                    items.getOrDefault(s.getId(), 0L).intValue()))
        .toList();
  }

  public SeriesDetailView getMine(Long userId, Long seriesId) {
    SeriesEntity series = seriesOwnership.requireOwned(userId, seriesId);
    List<PostEntity> members = postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
    List<SeriesItemEntity> rows = seriesItemRepository.findBySeriesId(seriesId);
    Map<Long, PostEntity> posts =
        members.stream().collect(Collectors.toMap(PostEntity::getId, Function.identity()));
    Map<Long, SeriesNote> notes =
        seriesItemReader.notes(
            rows.stream()
                .filter(r -> r.getType() == SeriesItemType.NOTE)
                .map(SeriesItemEntity::getRefId)
                .toList());
    List<SeriesItemView> items = new ArrayList<>(rows.size());
    for (SeriesItemEntity row : rows) {
      if (row.getType() == SeriesItemType.POST && posts.containsKey(row.getRefId())) {
        items.add(
            new SeriesItemView(
                row.getType().name(), PostView.from(posts.get(row.getRefId())), null));
      } else if (row.getType() == SeriesItemType.NOTE && notes.containsKey(row.getRefId())) {
        items.add(
            new SeriesItemView(
                row.getType().name(), null, SeriesNoteView.from(notes.get(row.getRefId()))));
      }
    }
    return new SeriesDetailView(
        SeriesView.from(series, members.size(), items.size()),
        members.stream().map(PostView::from).toList(),
        items);
  }
}
