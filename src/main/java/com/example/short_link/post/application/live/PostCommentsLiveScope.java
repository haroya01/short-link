package com.example.short_link.post.application.live;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveScopeReader;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostCommentsLiveScope implements LiveScopeReader {

  private final PostRepository posts;
  private final CommentRepository comments;
  private final PostAccessPolicy access;

  @Override
  public LiveTopic.Scope scope() {
    return LiveTopic.Scope.POST;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LiveScope> open(long id, long viewerId, String clientIp) {
    PostViewer viewer = new PostViewer(viewerId, null, null, clientIp, false);
    return posts
        .findById(id)
        .filter(post -> access.decide(post, viewer).full())
        .map(post -> new LiveScope(post.getUserId(), post.getRoomId()));
  }

  @Override
  @Transactional(readOnly = true)
  public long latest(long id) {
    return comments.latestId(id);
  }
}
