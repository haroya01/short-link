package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 하이라이트 작성자에게 답글 알림을 보내며 멘션 알림은 댓글과 같은 정책으로 처리한다. */
@Service
@RequiredArgsConstructor
public class CreateHighlightReplyUseCase {

  private final PostInteractionAccess access;
  private final PostHighlightReplyRepository replyRepository;
  private final UserRepository userRepository;
  private final CommentNotifications notifications;

  @Transactional
  public HighlightReplyView execute(CreateHighlightReplyCommand cmd) {
    PostHighlightEntity highlight =
        access.requireReplyableHighlight(cmd.userId(), cmd.highlightId());

    PostHighlightReplyEntity saved =
        replyRepository.save(
            new PostHighlightReplyEntity(highlight.getId(), cmd.userId(), cmd.body().trim()));

    notifications.highlightReplyCreated(highlight, saved, cmd.body());

    UserEntity author = userRepository.findById(cmd.userId()).orElse(null);
    return new HighlightReplyView(
        saved.getId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt());
  }
}
