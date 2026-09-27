package com.example.short_link.post.application.write;

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
public class DeleteSeriesUseCase {

  private final SeriesOwnership seriesOwnership;
  private final SeriesRepository seriesRepository;
  private final PostRepository postRepository;

  @Transactional
  public void execute(DeleteSeriesCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwnedForUpdate(cmd.userId(), cmd.seriesId());
    // series_id는 FK가 없으므로 시리즈 삭제 전에 글에서 참조를 지운다.
    for (PostEntity post :
        postRepository.findSeriesMembersAndRequestedForUpdate(series.getId(), List.of())) {
      post.clearSeries();
      postRepository.save(post);
    }
    seriesRepository.delete(series);
  }
}
