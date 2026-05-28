package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a series' ordered membership. Each listed post must be owned by the caller; posts are
 * assigned 0-based {@code seriesOrder} in list order. Posts previously in the series but absent
 * from the new list are detached.
 */
@Service
@RequiredArgsConstructor
public class SetSeriesPostsUseCase {

  private final SeriesOwnership seriesOwnership;
  private final PostOwnership postOwnership;
  private final PostRepository postRepository;

  @Transactional
  public void execute(SetSeriesPostsCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwned(cmd.userId(), cmd.seriesId());
    List<Long> postIds = cmd.postIds();
    Set<Long> keep = new HashSet<>(postIds);

    // Detach posts that drop out of the series.
    for (PostEntity existing :
        postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(series.getId())) {
      if (!keep.contains(existing.getId())) {
        existing.clearSeries();
        postRepository.save(existing);
      }
    }

    // Assign / reorder the listed posts (ownership-checked).
    for (int order = 0; order < postIds.size(); order++) {
      PostEntity post = postOwnership.requireOwned(cmd.userId(), postIds.get(order));
      post.assignToSeries(series.getId(), order);
      postRepository.save(post);
    }
  }
}
