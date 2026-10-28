package com.example.short_link.post.application.write;

import com.example.short_link.post.application.access.PostViewer;
import com.example.short_link.post.application.read.CommentMentions;
import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateHighlightReplyUseCase {

  private final PostInteractionAccess access;
  private final PostHighlightReplyRepository replyRepository;
  private final UserSummaryReader users;
  private final CommentNotifications notifications;
  private final CommentMentions mentions;

  @Transactional
  public HighlightReplyView execute(CreateHighlightReplyCommand cmd, PostViewer reader) {
    PostHighlightEntity highlight = access.requireReplyableHighlight(reader, cmd.highlightId());

    PostHighlightReplyEntity saved =
        replyRepository.save(
            new PostHighlightReplyEntity(highlight.getId(), cmd.userId(), cmd.body().trim()));

    notifications.highlightReplyCreated(highlight, saved, cmd.body());

    UserSummary author = users.find(cmd.userId()).orElse(null);
    return new HighlightReplyView(
        saved.getId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt(),
        mentions.of(saved.getBody()),
        0,
        false);
  }
}
