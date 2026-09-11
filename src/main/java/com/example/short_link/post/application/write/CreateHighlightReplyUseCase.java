package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Create a reply in a highlight's flat thread. The highlight must exist; the reply notifies the
 * highlight's author (the thread opener) and any @-mentioned users, reusing the in-app REPLY /
 * MENTION fan-out — the same shape as {@link CreateCommentUseCase}.
 */
@Service
@RequiredArgsConstructor
public class CreateHighlightReplyUseCase {

  private final PostHighlightRepository highlightRepository;
  private final PostHighlightReplyRepository replyRepository;
  private final UserRepository userRepository;
  private final CommentNotifications notifications;

  @Transactional
  public HighlightReplyView execute(CreateHighlightReplyCommand cmd) {
    PostHighlightEntity highlight =
        highlightRepository
            .findById(cmd.highlightId())
            .orElseThrow(
                () -> new PostException(PostErrorCode.HIGHLIGHT_NOT_FOUND, cmd.highlightId()));

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
