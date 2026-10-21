package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.BlogInteractionEvent;
import com.example.short_link.common.event.CommentMentionEvent;
import com.example.short_link.common.event.CommentReplyEvent;
import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.common.notification.BlogNotificationMuteReader;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostGrants;
import com.example.short_link.post.application.access.ReaderCountryResolver;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CommentNotificationsRoomTest {

  private static final long AUTHOR = 1L;
  private static final long MEMBER = 2L;
  private static final long STRANGER = 3L;
  private static final long LEFT = 4L;
  private static final long ROOMMATE = 5L;
  private static final long ROOM = 9L;

  private final UserRepository users = mock(UserRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final PostRoomReader rooms = mock(PostRoomReader.class);
  private CommentNotifications notifications;

  @BeforeEach
  void setUp() {
    GeoIpResolver geo = mock(GeoIpResolver.class, call -> new GeoLocation(null, null, null));
    PostAccessPolicy policy =
        new PostAccessPolicy(
            mock(FollowRepository.class),
            new PostGrants("", Clock.systemUTC()),
            new ReaderCountryResolver(geo),
            rooms);
    notifications =
        new CommentNotifications(
            users,
            mock(PostRepository.class),
            events,
            mock(BlogNotificationMuteReader.class),
            policy);
    when(rooms.membersAmong(eq(ROOM), anyCollection()))
        .thenAnswer(
            call -> {
              Collection<Long> asked = call.getArgument(1);
              Set<Long> members = new java.util.HashSet<>(asked);
              members.retainAll(Set.of(AUTHOR, MEMBER, ROOMMATE));
              return members;
            });
    for (long id : List.of(AUTHOR, MEMBER, STRANGER, LEFT, ROOMMATE)) {
      UserEntity user = TestEntities.withId(new UserEntity(id + "@x.com", "google", "g" + id), id);
      user.claimUsername("user" + id);
      when(users.findByUsername("user" + id)).thenReturn(Optional.of(user));
      when(users.findById(id)).thenReturn(Optional.of(user));
    }
  }

  @Test
  void aRoomPostTellsOnlyMembersAboutWhatIsSaidOnIt() {
    PostEntity post = post(PostAudience.ROOM);
    CommentEntity parent = TestEntities.withId(new CommentEntity(42L, LEFT, null, "hi"), 7L);
    CommentEntity reply = TestEntities.withId(new CommentEntity(42L, MEMBER, 7L, "re"), 8L);

    notifications.commentCreated(post, parent, reply, "re @user3 @user4 @user5");

    List<Object> published = published();
    assertThat(published)
        .filteredOn(CommentMentionEvent.class::isInstance)
        .extracting(event -> ((CommentMentionEvent) event).recipientUserId())
        .containsExactly(ROOMMATE);
    assertThat(published).noneMatch(CommentReplyEvent.class::isInstance);
    assertThat(published)
        .filteredOn(BlogInteractionEvent.class::isInstance)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(((BlogInteractionEvent) event).recipientUserId()).isEqualTo(AUTHOR);
              assertThat(((BlogInteractionEvent) event).roomPost()).isTrue();
            });
  }

  @Test
  void anOpenPostStillTellsWhoeverIsNamed() {
    PostEntity post = post(PostAudience.PUBLIC);
    CommentEntity comment = TestEntities.withId(new CommentEntity(42L, MEMBER, null, "x"), 8L);

    notifications.commentCreated(post, null, comment, "x @user3");

    assertThat(published())
        .filteredOn(CommentMentionEvent.class::isInstance)
        .extracting(event -> ((CommentMentionEvent) event).recipientUserId())
        .containsExactly(STRANGER);
  }

  private List<Object> published() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
    return new ArrayList<>(captor.getAllValues());
  }

  private PostEntity post(PostAudience audience) {
    PostEntity post = TestEntities.withId(new PostEntity(AUTHOR, "s", "T", "ko"), 42L);
    post.publish();
    post.changeAudience(audience, audience.inRoom() ? ROOM : null, "key");
    return post;
  }
}
