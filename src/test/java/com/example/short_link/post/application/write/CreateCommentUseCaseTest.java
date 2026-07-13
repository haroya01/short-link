package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.common.user.UserBlockChecker;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateCommentUseCaseTest {

  @Mock private PostRepository postRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private UserRepository userRepository;
  @Mock private org.springframework.context.ApplicationEventPublisher events;
  @Mock private BlogNotificationMuteReader muteReader;
  @Mock private UserModerationGuard moderationGuard;
  @Mock private UserBlockChecker blockChecker;

  private CreateCommentUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CreateCommentUseCase(
            postRepository,
            commentRepository,
            userRepository,
            events,
            muteReader,
            moderationGuard,
            blockChecker);
  }

  private PostEntity publishedPost() {
    PostEntity p = new PostEntity(7L, "slug", "Title", "ko");
    p.publish();
    return p;
  }

  private UserEntity commenter() {
    UserEntity u = new UserEntity("c@x.com", "google", "g-9");
    u.claimUsername("carol");
    return u;
  }

  private UserEntity owner() {
    UserEntity u = new UserEntity("o@x.com", "google", "g-7");
    u.claimUsername("olivia");
    return u;
  }

  private UserEntity userWithId(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void createsTopLevelComment() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost()));
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));

    CommentView c = useCase.execute(new CreateCommentCommand(9L, 42L, null, "  hello  "));

    assertThat(c.body()).isEqualTo("hello");
    assertThat(c.parentId()).isNull();
    assertThat(c.author().username()).isEqualTo("carol");
    // The post owner (7L ≠ commenter 9L) is notified of the new comment.
    org.mockito.ArgumentCaptor<com.example.short_link.common.event.BlogInteractionEvent> evt =
        org.mockito.ArgumentCaptor.forClass(
            com.example.short_link.common.event.BlogInteractionEvent.class);
    verify(events).publishEvent(evt.capture());
    assertThat(evt.getValue().type())
        .isEqualTo(com.example.short_link.common.event.BlogInteractionType.COMMENT);
    assertThat(evt.getValue().recipientUserId()).isEqualTo(7L);
  }

  @Test
  void rejectsWhenPostNotPublished() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(new PostEntity(7L, "s", "T", "ko")));

    assertThatThrownBy(() -> useCase.execute(new CreateCommentCommand(9L, 42L, null, "hi")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.POST_NOT_FOUND);
  }

  @Test
  void rejectsWhenPostAuthorBlockedCommenter() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    // 글 작성자(7)가 댓글 작성자(9)를 차단한 상태.
    when(blockChecker.isBlocked(7L, 9L)).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateCommentCommand(9L, 42L, null, "hi")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_BLOCKED);
    verify(commentRepository, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void replyNotifiesBothPostOwnerAndParentCommentAuthor() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    CommentEntity parent =
        new CommentEntity(42L, 3L, null, "top"); // parent author 3 (a third party)
    when(commentRepository.findById(50L)).thenReturn(Optional.of(parent));
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));

    useCase.execute(new CreateCommentCommand(9L, 42L, 50L, "a reply"));

    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(evt.capture());
    List<Object> events = evt.getAllValues();
    // COMMENT to the post owner (7), REPLY to the parent comment's author (3).
    BlogInteractionEvent comment =
        events.stream()
            .filter(e -> e instanceof BlogInteractionEvent)
            .map(e -> (BlogInteractionEvent) e)
            .findFirst()
            .orElseThrow();
    CommentReplyEvent reply =
        events.stream()
            .filter(e -> e instanceof CommentReplyEvent)
            .map(e -> (CommentReplyEvent) e)
            .findFirst()
            .orElseThrow();
    assertThat(comment.recipientUserId()).isEqualTo(7L);
    assertThat(reply.recipientUserId()).isEqualTo(3L);
    assertThat(reply.actorUserId()).isEqualTo(9L);
    // The reply carries the post owner's handle (not the recipient's) so the link resolves.
    assertThat(reply.postAuthorUsername()).isEqualTo("olivia");
  }

  @Test
  void replyToPostOwnersOwnCommentSendsOnlyTheReplyNotice() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    CommentEntity parent =
        new CommentEntity(42L, 7L, null, "owner's top"); // parent author == owner
    when(commentRepository.findById(50L)).thenReturn(Optional.of(parent));
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));

    useCase.execute(new CreateCommentCommand(9L, 42L, 50L, "a reply"));

    // Owner would get both COMMENT and REPLY — collapse to the single, more specific REPLY.
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(CommentReplyEvent.class);
    assertThat(((CommentReplyEvent) evt.getValue()).recipientUserId()).isEqualTo(7L);
  }

  @Test
  void replyingToYourOwnCommentSendsNoReplyNotice() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    CommentEntity parent =
        new CommentEntity(42L, 9L, null, "my own top"); // parent author == actor 9
    when(commentRepository.findById(50L)).thenReturn(Optional.of(parent));
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));

    useCase.execute(new CreateCommentCommand(9L, 42L, 50L, "a reply"));

    // Only the post-owner COMMENT fires; the self-reply produces no REPLY.
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(BlogInteractionEvent.class);
  }

  @Test
  void mentionNotifiesTheMentionedUser() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(userWithId(5L, "bob")));

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "hey @bob nice post"));

    // COMMENT to the owner (7) + MENTION to the mentioned user (5).
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(evt.capture());
    CommentMentionEvent mention =
        evt.getAllValues().stream()
            .filter(e -> e instanceof CommentMentionEvent)
            .map(e -> (CommentMentionEvent) e)
            .findFirst()
            .orElseThrow();
    assertThat(mention.recipientUserId()).isEqualTo(5L);
    assertThat(mention.actorUserId()).isEqualTo(9L);
    assertThat(mention.postAuthorUsername()).isEqualTo("olivia");
  }

  @Test
  void mentioningThePostOwnerCollapsesIntoTheCommentNotice() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("olivia")).thenReturn(Optional.of(userWithId(7L, "olivia")));

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "thanks @olivia"));

    // Owner would get COMMENT and a MENTION — collapse to the single COMMENT notice.
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(BlogInteractionEvent.class);
  }

  @Test
  void mentioningSomeoneWhoMutedCommentStillFiresTheirMention() {
    // The owner (7) muted COMMENT but keeps MENTION on. A COMMENT collapse would drop their bell
    // entirely — so an explicit @-mention of them must NOT fold into a notice they won't receive.
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("olivia")).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(muteReader.isMuted(7L, BlogNotificationKind.COMMENT)).thenReturn(true);

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "thanks @olivia"));

    // Both events still publish (COMMENT is dropped downstream by the mute; the MENTION survives so
    // the explicitly-mentioned owner is reachable through their still-on MENTION preference).
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(evt.capture());
    CommentMentionEvent mention =
        evt.getAllValues().stream()
            .filter(e -> e instanceof CommentMentionEvent)
            .map(e -> (CommentMentionEvent) e)
            .findFirst()
            .orElseThrow();
    assertThat(mention.recipientUserId()).isEqualTo(7L);
    assertThat(mention.actorUserId()).isEqualTo(9L);
  }

  @Test
  void mentioningTheOwnerWithoutAnyMuteFiresExactlyOneCommentNotice() {
    // Symmetric guard: with no mute, the owner's COMMENT is delivered, so their @-mention collapses
    // into it — exactly one event, no COMMENT+MENTION duplicate.
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("olivia")).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(muteReader.isMuted(7L, BlogNotificationKind.COMMENT)).thenReturn(false);

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "thanks @olivia"));

    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(BlogInteractionEvent.class);
  }

  @Test
  void selfMentionSendsNoMentionNotice() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("carol")).thenReturn(Optional.of(userWithId(9L, "carol")));

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "it's me @carol"));

    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture()); // COMMENT only
    assertThat(evt.getValue()).isInstanceOf(BlogInteractionEvent.class);
  }

  @Test
  void unknownMentionHandleIsSkipped() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(commentRepository.save(any(CommentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(commenter()));
    when(userRepository.findById(7L)).thenReturn(Optional.of(owner()));
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    useCase.execute(new CreateCommentCommand(9L, 42L, null, "@ghost are you there"));

    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture()); // COMMENT only
    assertThat(evt.getValue()).isInstanceOf(BlogInteractionEvent.class);
  }

  @Test
  void rejectsReplyToReply() {
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost()));
    CommentEntity parentReply = new CommentEntity(42L, 1L, 5L, "i am a reply");
    when(commentRepository.findById(99L)).thenReturn(Optional.of(parentReply));

    assertThatThrownBy(() -> useCase.execute(new CreateCommentCommand(9L, 42L, 99L, "nested")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.COMMENT_PARENT_INVALID);
  }
}
