package com.example.short_link.post.application.write;

import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;

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

    Long actorId = cmd.userId();
    Long highlightAuthorId = highlight.getUserId();
    // Everyone this one reply has already notified — so a @-mention of the same person collapses
    // into the REPLY they already get (one notice per person).
    Set<Long> notified = new HashSet<>();

    // REPLY and MENTION links point at the post, whose owner may not be the recipient — so both
    // carry the post owner's username. Resolve the post + owner handle once, only when one of them
    // will actually fire.
    List<String> mentionedHandles = MentionParser.parse(cmd.body());
    boolean notifyAuthor = !highlightAuthorId.equals(actorId);
    boolean needsPost = notifyAuthor || !mentionedHandles.isEmpty();
    PostEntity post =
        needsPost ? postRepository.findById(highlight.getPostId()).orElse(null) : null;
    String ownerUsername =
        post == null
            ? null
            : userRepository.findById(post.getUserId()).map(UserEntity::getUsername).orElse(null);

    // Notify the highlight's author of a reply — never for replying to your own highlight.
    if (notifyAuthor && post != null) {
      events.publishEvent(
          new HighlightReplyEvent(
              highlightAuthorId,
              actorId,
              post.getId(),
              post.getSlug(),
              post.getTitle(),
              ownerUsername,
              saved.getCreatedAt()));
      notified.add(highlightAuthorId);
    }

    // @-mentions — one notice per mentioned user, skipping self, unknown handles, and anyone the
    // REPLY above already notified (notified.add returns false → skip).
    if (post != null) {
      for (String handle : mentionedHandles) {
        Long mentionedId =
            userRepository.findByUsername(handle).map(UserEntity::getId).orElse(null);
        if (mentionedId == null || mentionedId.equals(actorId) || !notified.add(mentionedId)) {
          continue;
        }
        events.publishEvent(
            new HighlightMentionEvent(
                mentionedId,
                actorId,
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                ownerUsername,
                saved.getCreatedAt()));
      }
    }

    UserEntity author = userRepository.findById(actorId).orElse(null);
    return new HighlightReplyView(
        saved.getId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt());
  }
}
