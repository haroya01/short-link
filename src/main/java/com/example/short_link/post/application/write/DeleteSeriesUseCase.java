package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.SeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteSeriesUseCase {

  private final SeriesOwnership seriesOwnership;
  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;

  @Transactional
  public void execute(DeleteSeriesCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwned(cmd.userId(), cmd.seriesId());
    // Detach member posts first — series_id is a soft reference, so leaving them set would
    // orphan the posts behind a series that no longer exists.
    for (PostEntity post : postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(series.getId())) {
      post.clearSeries();
      postRepository.save(post);
    }
    seriesRepository.delete(series);
  }
}
