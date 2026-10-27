package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.event.HighlightMentionEvent;
import com.example.short_link.common.event.HighlightReplyEvent;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.post.application.access.PostAccessFixtures;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.application.read.UserSummaryReader;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class CommentNotificationsTest {

  private static final long OWNER = 1L;
  private static final long ACTOR = 2L;
  private static final long PARENT = 3L;
  private static final long BOB = 5L;

  private final UserRepository users = mock(UserRepository.class);
  private final PostRepository posts = mock(PostRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final CommentNotifications notifications =
      new CommentNotifications(
          new UserSummaryReader(users),
          posts,
          events,
          mock(BlogNotificationMuteReader.class),
          PostAccessFixtures.policy());
  private PostEntity post;

  @BeforeEach
  void setUp() {
    post = TestEntities.withId(new PostEntity(OWNER, "s", "T", "ko"), 42L);
    post.publish();
    for (long id : List.of(OWNER, ACTOR, PARENT, BOB)) {
      UserEntity user = TestEntities.withId(new UserEntity(id + "@x.com", "google", "g" + id), id);
      user.claimUsername("user" + id);
      when(users.findByUsername("user" + id)).thenReturn(Optional.of(user));
      when(users.findById(id)).thenReturn(Optional.of(user));
    }
  }

  @Test
  void aCommentReadsTheOwnersNameBeforeLookingUpTheMentions() {
    CommentEntity parent = TestEntities.withId(new CommentEntity(42L, PARENT, null, "p"), 7L);
    CommentEntity comment = TestEntities.withId(new CommentEntity(42L, ACTOR, 7L, "c"), 8L);

    notifications.commentCreated(post, parent, comment, "@user1 @user5");

    InOrder order = inOrder(users);
    order.verify(users).findById(OWNER);
    order.verify(users).findByUsername("user1");
    order.verify(users).findByUsername("user5");
  }

  @Test
  void aCommentsNoticesGoOutAsCommentThenReplyThenMentions() {
    CommentEntity parent = TestEntities.withId(new CommentEntity(42L, PARENT, null, "p"), 7L);
    CommentEntity comment = TestEntities.withId(new CommentEntity(42L, ACTOR, 7L, "c"), 8L);

    notifications.commentCreated(post, parent, comment, "@user5");

    assertThat(published())
        .extracting(Object::getClass)
        .containsExactly(
            BlogInteractionEvent.class, CommentReplyEvent.class, CommentMentionEvent.class);
  }

  @Test
  void aHighlightReplyReadsTheOwnersNameBeforeLookingUpTheMentions() {
    PostHighlightEntity highlight =
        TestEntities.withId(new PostHighlightEntity(42L, PARENT, 0, 0, 0, 3, "abc", null), 6L);
    PostHighlightReplyEntity reply =
        TestEntities.withId(new PostHighlightReplyEntity(6L, ACTOR, "r"), 9L);
    when(posts.findById(42L)).thenReturn(Optional.of(post));

    notifications.highlightReplyCreated(highlight, reply, "@user1 @user5");

    InOrder order = inOrder(users);
    order.verify(users).findById(OWNER);
    order.verify(users).findByUsername("user1");
    order.verify(users).findByUsername("user5");
    assertThat(published())
        .extracting(Object::getClass)
        .containsExactly(
            HighlightReplyEvent.class, HighlightMentionEvent.class, HighlightMentionEvent.class);
  }

  private List<Object> published() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    Mockito.verify(events, Mockito.atLeast(0)).publishEvent(captor.capture());
    return captor.getAllValues();
  }
}
