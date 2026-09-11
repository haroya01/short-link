package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.SeriesRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeriesOwnership {

  private final SeriesRepository seriesRepository;

  public SeriesEntity requireOwned(Long userId, Long seriesId) {
    return verifyOwner(
        seriesRepository
            .findById(seriesId)
            .orElseThrow(() -> new PostException(PostErrorCode.SERIES_NOT_FOUND, seriesId)),
        userId,
        seriesId);
  }

  /** Lock the series before its member posts so deletion and membership changes are serialized. */
  public SeriesEntity requireOwnedForUpdate(Long userId, Long seriesId) {
    return verifyOwner(
        seriesRepository
            .findByIdForUpdate(seriesId)
            .orElseThrow(() -> new PostException(PostErrorCode.SERIES_NOT_FOUND, seriesId)),
        userId,
        seriesId);
  }

  private SeriesEntity verifyOwner(SeriesEntity series, Long userId, Long seriesId) {
    if (!series.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.SERIES_PERMISSION_DENIED).with("seriesId", seriesId);
    }
    return series;
  }
}
