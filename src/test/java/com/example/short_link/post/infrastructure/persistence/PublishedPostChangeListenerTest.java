package com.example.short_link.post.infrastructure.persistence;

import static com.example.short_link.post.domain.PostStatus.DRAFT;
import static com.example.short_link.post.domain.PostStatus.PUBLISHED;
import static com.example.short_link.post.domain.PostStatus.SCHEDULED;
import static com.example.short_link.post.domain.PostStatus.UNPUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.WebCacheRevalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.user.domain.UserEntity;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class PublishedPostChangeListenerTest {

  private static final String[] NAMES = {
    "humanReaderCount", "lastEditedAt", "likeCount", "status", "title", "updatedAt", "viewCount"
  };

  private final WebCacheRevalidator revalidator = mock(WebCacheRevalidator.class);
  private final PublishedPostChangeListener listener =
      new PublishedPostChangeListener(mock(EntityManagerFactory.class), revalidator);
  private final EntityPersister posts = persister(PostEntity.class);

  private static EntityPersister persister(Class<?> mapped) {
    EntityPersister persister = mock(EntityPersister.class);
    when(persister.getMappedClass()).thenAnswer(invocation -> mapped);
    when(persister.getPropertyNames()).thenReturn(NAMES);
    return persister;
  }

  private static Object[] state(PostStatus status) {
    Object[] state = new Object[NAMES.length];
    state[Arrays.asList(NAMES).indexOf("status")] = status;
    return state;
  }

  private static int[] dirty(String... properties) {
    return Arrays.stream(properties).mapToInt(Arrays.asList(NAMES)::indexOf).toArray();
  }

  private PostUpdateEvent update(Object[] before, Object[] after, int[] dirty) {
    return new PostUpdateEvent(null, 1L, after, before, dirty, posts, null);
  }

  static Stream<Arguments> updates() {
    return Stream.of(
        Arguments.of(DRAFT, DRAFT, List.of("title", "lastEditedAt", "updatedAt"), 0),
        Arguments.of(DRAFT, SCHEDULED, List.of("status", "updatedAt"), 0),
        Arguments.of(UNPUBLISHED, UNPUBLISHED, List.of("title", "updatedAt"), 0),
        Arguments.of(DRAFT, PUBLISHED, List.of("status", "updatedAt"), 1),
        Arguments.of(SCHEDULED, PUBLISHED, List.of("status", "updatedAt"), 1),
        Arguments.of(UNPUBLISHED, PUBLISHED, List.of("status", "updatedAt"), 1),
        Arguments.of(PUBLISHED, UNPUBLISHED, List.of("status", "updatedAt"), 1),
        Arguments.of(PUBLISHED, PUBLISHED, List.of("title", "lastEditedAt", "updatedAt"), 1),
        Arguments.of(
            PUBLISHED, PUBLISHED, List.of("viewCount", "humanReaderCount", "updatedAt"), 0),
        Arguments.of(PUBLISHED, PUBLISHED, List.of("likeCount"), 0));
  }

  @ParameterizedTest(name = "{0} -> {1} changing {2} asks {3} time(s)")
  @MethodSource("updates")
  void anUpdateAsksOnlyWhenAPublishedPostChangesMoreThanItsCounters(
      PostStatus before, PostStatus after, List<String> changed, int asks) {
    listener.onPostUpdate(
        update(state(before), state(after), dirty(changed.toArray(String[]::new))));

    verify(revalidator, times(asks)).requestRevalidation();
  }

  @Test
  void anUpdateWhoseEarlierStateOrDirtinessHibernateCouldNotTellAsks() {
    listener.onPostUpdate(update(null, state(DRAFT), dirty("title")));
    listener.onPostUpdate(update(state(PUBLISHED), state(PUBLISHED), null));

    verify(revalidator, times(2)).requestRevalidation();
  }

  @ParameterizedTest
  @EnumSource(PostStatus.class)
  void anInsertOrDeleteAsksOnlyForAPublishedPost(PostStatus status) {
    listener.onPostInsert(new PostInsertEvent(null, 1L, state(status), posts, null));
    listener.onPostDelete(new PostDeleteEvent(null, 1L, state(status), posts, null));

    verify(revalidator, times(status == PUBLISHED ? 2 : 0)).requestRevalidation();
  }

  @Test
  void otherEntitiesAreNeitherHeldPastTheCommitNorHeard() {
    EntityPersister users = persister(UserEntity.class);

    assertThat(listener.requiresPostCommitHandling(users)).isFalse();
    assertThat(listener.requiresPostCommitHandling(posts)).isTrue();
    listener.onPostInsert(new PostInsertEvent(null, 1L, state(PUBLISHED), users, null));
    listener.onPostUpdate(
        new PostUpdateEvent(
            null, 1L, state(PUBLISHED), state(DRAFT), dirty("status"), users, null));
    listener.onPostDelete(new PostDeleteEvent(null, 1L, state(PUBLISHED), users, null));

    verifyNoInteractions(revalidator);
  }

  @Test
  void aRolledBackChangeAsksNothing() {
    listener.onPostInsertCommitFailed(new PostInsertEvent(null, 1L, state(PUBLISHED), posts, null));
    listener.onPostUpdateCommitFailed(
        update(state(DRAFT), state(PUBLISHED), dirty("status", "updatedAt")));
    listener.onPostDeleteCommitFailed(new PostDeleteEvent(null, 1L, state(PUBLISHED), posts, null));

    verifyNoInteractions(revalidator);
  }
}
