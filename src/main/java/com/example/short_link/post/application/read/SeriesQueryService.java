package com.example.short_link.post.application.read;

import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeriesQueryService {

  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;
  private final SeriesOwnership seriesOwnership;

  public List<SeriesView> listMine(Long userId) {
    return seriesRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(
            s ->
                SeriesView.from(
                    s, postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(s.getId()).size()))
        .toList();
  }

  public SeriesDetailView getMine(Long userId, Long seriesId) {
    SeriesEntity series = seriesOwnership.requireOwned(userId, seriesId);
    List<PostEntity> members = postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId);
    return new SeriesDetailView(
        SeriesView.from(series, members.size()), members.stream().map(PostView::from).toList());
  }
}
