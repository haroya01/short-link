package com.example.short_link.post.application.write;

import com.example.short_link.common.room.RoomPostRemover;
import com.example.short_link.post.domain.repository.RoomPostErasureRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Each kind goes through its usual delete, so a comment with replies leaves its place as when
// deleted by hand. The request is done once every kind comes back short, and running it again after
// a crash only finds what is left.
@Service
@RequiredArgsConstructor
public class RoomPostEraser implements RoomPostRemover {

  private final RoomPostErasureRepository erasures;
  private final DeletePostUseCase deletePost;
  private final DeleteCommentUseCase deleteComment;
  private final CreateHighlightUseCase highlights;
  private final DeleteHighlightReplyUseCase deleteHighlightReply;
  private final Clock clock;

  @Override
  @Transactional
  public void erasePostsBy(Long userId, Long roomId) {
    erasures.request(userId, roomId, clock.instant().truncatedTo(ChronoUnit.MICROS));
  }

  @Transactional
  public int eraseChunk(Long userId, Long roomId, int chunk) {
    List<Long> posts = erasures.postIdsBy(userId, roomId, chunk);
    posts.forEach(id -> deletePost.execute(new DeletePostCommand(userId, id)));
    List<Long> replies = erasures.highlightReplyIdsBy(userId, roomId, chunk);
    replies.forEach(
        id -> deleteHighlightReply.execute(new DeleteHighlightReplyCommand(userId, id)));
    List<Long> said = erasures.highlightIdsBy(userId, roomId, chunk);
    said.forEach(id -> highlights.delete(userId, id));
    List<Long> comments = erasures.commentIdsBy(userId, roomId, chunk);
    comments.forEach(id -> deleteComment.execute(new DeleteCommentCommand(userId, id)));
    if (posts.size() < chunk
        && replies.size() < chunk
        && said.size() < chunk
        && comments.size() < chunk) {
      erasures.finish(userId, roomId);
    }
    return posts.size() + replies.size() + said.size() + comments.size();
  }
}
