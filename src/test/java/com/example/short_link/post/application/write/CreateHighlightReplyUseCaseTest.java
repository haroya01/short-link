package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.notification.BlogNotificationKind;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.post.application.read.HighlightReplyView;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CreateHighlightReplyUseCaseTest {

  @Mock private PostHighlightRepository highlightRepository;
  @Mock private PostHighlightReplyRepository replyRepository;
  @Mock private PostRepository postRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher events;
  @Mock private BlogNotificationMuteReader muteReader;

  private CreateHighlightReplyUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new CreateHighlightReplyUseCase(
            highlightRepository,
            replyRepository,
            postRepository,
            userRepository,
            events,
            muteReader);
  }

  /** Highlight 50 on post 42, authored by {@code authorId}. */
  private PostHighlightEntity highlight(long authorId) {
    PostHighlightEntity h = new PostHighlightEntity(42L, authorId, 0, 0, 0, 3, "quote", null);
    ReflectionTestUtils.setField(h, "id", 50L);
    return h;
  }

  private PostEntity publishedPost() {
    PostEntity p = new PostEntity(7L, "slug", "Title", "ko"); // post owner 7
    p.publish();
    return p;
  }

  private UserEntity userWithId(long id, String username) {
    UserEntity u = new UserEntity("u" + id + "@x.com", "google", "g-" + id);
    u.claimUsername(username);
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void createsReplyAndNotifiesHighlightAuthor() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(3L))); // author 3
    when(replyRepository.save(any(PostHighlightReplyEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(userRepository.findById(7L)).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(userWithId(9L, "carol")));

    HighlightReplyView view = useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "  동의  "));

    assertThat(view.body()).isEqualTo("동의");
    assertThat(view.author().username()).isEqualTo("carol");

    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(HighlightReplyEvent.class);
    HighlightReplyEvent reply = (HighlightReplyEvent) evt.getValue();
    assertThat(reply.recipientUserId()).isEqualTo(3L); // highlight author
    assertThat(reply.actorUserId()).isEqualTo(9L);
    // Carries the post owner's handle (not the recipient's) so the post link resolves.
    assertThat(reply.postAuthorUsername()).isEqualTo("olivia");
  }

  @Test
  void rejectsWhenHighlightMissing() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "hi")))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.HIGHLIGHT_NOT_FOUND);
  }

  @Test
  void replyingToYourOwnHighlightSendsNoReplyNotice() {
    when(highlightRepository.findById(50L))
        .thenReturn(Optional.of(highlight(9L))); // author == actor
    when(replyRepository.save(any(PostHighlightReplyEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(9L)).thenReturn(Optional.of(userWithId(9L, "carol")));

    useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "내 하이라이트에 셀프 답글"));

    verify(events, times(0)).publishEvent(any());
  }

  @Test
  void mentionNotifiesTheMentionedUser() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(3L))); // author 3
    when(replyRepository.save(any(PostHighlightReplyEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(userRepository.findById(7L)).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(userWithId(9L, "carol")));
    when(userRepository.findByUsername("bob")).thenReturn(Optional.of(userWithId(5L, "bob")));

    useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "good point @bob"));

    // REPLY to the highlight author (3) + MENTION to the mentioned user (5).
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(evt.capture());
    HighlightMentionEvent mention =
        evt.getAllValues().stream()
            .filter(e -> e instanceof HighlightMentionEvent)
            .map(e -> (HighlightMentionEvent) e)
            .findFirst()
            .orElseThrow();
    assertThat(mention.recipientUserId()).isEqualTo(5L);
    assertThat(mention.actorUserId()).isEqualTo(9L);
    assertThat(mention.postAuthorUsername()).isEqualTo("olivia");
  }

  @Test
  void mentioningTheHighlightAuthorCollapsesIntoTheReplyNotice() {
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(3L))); // author 3
    when(replyRepository.save(any(PostHighlightReplyEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(userRepository.findById(7L)).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(userWithId(9L, "carol")));
    when(userRepository.findByUsername("dan")).thenReturn(Optional.of(userWithId(3L, "dan")));

    useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "thanks @dan"));

    // Author 3 would get REPLY and a MENTION — collapse to the single REPLY notice.
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(1)).publishEvent(evt.capture());
    assertThat(evt.getValue()).isInstanceOf(HighlightReplyEvent.class);
  }

  @Test
  void mentioningTheHighlightAuthorWhoMutedReplyStillFiresTheirMention() {
    // Author 3 muted REPLY but keeps MENTION on. A REPLY collapse would drop their bell entirely —
    // so an explicit @-mention of them must NOT fold into a notice they won't receive.
    when(highlightRepository.findById(50L)).thenReturn(Optional.of(highlight(3L))); // author 3
    when(replyRepository.save(any(PostHighlightReplyEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(postRepository.findById(42L)).thenReturn(Optional.of(publishedPost())); // owner 7
    when(userRepository.findById(7L)).thenReturn(Optional.of(userWithId(7L, "olivia")));
    when(userRepository.findById(9L)).thenReturn(Optional.of(userWithId(9L, "carol")));
    when(userRepository.findByUsername("dan")).thenReturn(Optional.of(userWithId(3L, "dan")));
    when(muteReader.isMuted(3L, BlogNotificationKind.REPLY)).thenReturn(true);

    useCase.execute(new CreateHighlightReplyCommand(9L, 50L, "thanks @dan"));

    // Both events publish (REPLY dropped downstream by the mute; MENTION survives so the explicitly
    // mentioned author is reachable through their still-on MENTION preference).
    ArgumentCaptor<Object> evt = ArgumentCaptor.forClass(Object.class);
    verify(events, times(2)).publishEvent(evt.capture());
    HighlightMentionEvent mention =
        evt.getAllValues().stream()
            .filter(e -> e instanceof HighlightMentionEvent)
            .map(e -> (HighlightMentionEvent) e)
            .findFirst()
            .orElseThrow();
    assertThat(mention.recipientUserId()).isEqualTo(3L);
    assertThat(mention.actorUserId()).isEqualTo(9L);
  }
}
