package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateSeriesUseCase {

  private final SeriesOwnership seriesOwnership;
  private final SeriesRepository seriesRepository;

  @Transactional
  public SeriesEntity execute(UpdateSeriesCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwned(cmd.userId(), cmd.seriesId());
    if (cmd.slug() != null && !cmd.slug().equals(series.getSlug())) {
      if (seriesRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
        throw new PostException(PostErrorCode.SERIES_SLUG_CONFLICT, cmd.slug());
      }
      series.updateSlug(cmd.slug());
    }
    if (cmd.title() != null) {
      series.updateTitle(cmd.title());
    }
    return seriesRepository.save(series);
  }
}
