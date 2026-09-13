package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.SeriesEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 목록 순서(0부터)로 본인 글을 배치하고, 목록에서 빠진 기존 멤버는 해제한다. */
@Service
@RequiredArgsConstructor
public class SetSeriesPostsUseCase {

  private final SeriesOwnership seriesOwnership;
  private final PostRepository postRepository;

  @Transactional
  public void execute(SetSeriesPostsCommand cmd) {
    SeriesEntity series = seriesOwnership.requireOwnedForUpdate(cmd.userId(), cmd.seriesId());
    List<Long> postIds = cmd.postIds();
    Set<Long> keep = new HashSet<>(postIds);
    // Lock the complete affected set before changing any row, regardless of requested order.
    List<PostEntity> affected =
        postRepository.findSeriesMembersAndRequestedForUpdate(series.getId(), postIds);
    Map<Long, PostEntity> byId = new HashMap<>();
    for (PostEntity post : affected) byId.put(post.getId(), post);

    for (Long postId : postIds) {
      PostEntity post = byId.get(postId);
      if (post == null) {
        throw new PostException(PostErrorCode.POST_NOT_FOUND, postId);
      }
      if (!post.isOwnedBy(cmd.userId())) {
        throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
      }
    }

    for (PostEntity existing : affected) {
      if (!keep.contains(existing.getId())) {
        existing.clearSeries();
        postRepository.save(existing);
      }
    }

    for (int order = 0; order < postIds.size(); order++) {
      PostEntity post = byId.get(postIds.get(order));
      post.assignToSeries(series.getId(), order);
      postRepository.save(post);
    }
  }
}
