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
public class CreateSeriesUseCase {

  private final SeriesRepository seriesRepository;

  @Transactional
  public SeriesEntity execute(CreateSeriesCommand cmd) {
    if (seriesRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
      throw new PostException(PostErrorCode.SERIES_SLUG_CONFLICT, cmd.slug());
    }
    return seriesRepository.save(new SeriesEntity(cmd.userId(), cmd.slug(), cmd.title()));
  }
}
