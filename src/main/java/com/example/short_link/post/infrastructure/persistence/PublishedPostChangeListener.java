package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.cache.WebCacheRevalidator;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublishedPostChangeListener
    implements PostCommitInsertEventListener,
        PostCommitUpdateEventListener,
        PostCommitDeleteEventListener {

  private static final Set<String> COUNTERS =
      Set.of("viewCount", "likeCount", "humanReaderCount", "updatedAt");

  private final EntityManagerFactory entityManagerFactory;
  private final WebCacheRevalidator revalidator;

  @PostConstruct
  void register() {
    EventListenerRegistry listeners =
        entityManagerFactory.unwrap(SessionFactoryImplementor.class).getEventListenerRegistry();
    listeners.appendListeners(EventType.POST_COMMIT_INSERT, this);
    listeners.appendListeners(EventType.POST_COMMIT_UPDATE, this);
    listeners.appendListeners(EventType.POST_COMMIT_DELETE, this);
  }

  @Override
  public boolean requiresPostCommitHandling(EntityPersister persister) {
    return persister.getMappedClass() == PostEntity.class;
  }

  @Override
  public void onPostInsert(PostInsertEvent event) {
    EntityPersister persister = event.getPersister();
    if (requiresPostCommitHandling(persister) && published(persister, event.getState())) {
      revalidator.requestRevalidation();
    }
  }

  @Override
  public void onPostUpdate(PostUpdateEvent event) {
    EntityPersister persister = event.getPersister();
    if (requiresPostCommitHandling(persister)
        && (published(persister, event.getOldState()) || published(persister, event.getState()))
        && changesMoreThanCounters(persister, event.getDirtyProperties())) {
      revalidator.requestRevalidation();
    }
  }

  @Override
  public void onPostDelete(PostDeleteEvent event) {
    EntityPersister persister = event.getPersister();
    if (requiresPostCommitHandling(persister) && published(persister, event.getDeletedState())) {
      revalidator.requestRevalidation();
    }
  }

  @Override
  public void onPostInsertCommitFailed(PostInsertEvent event) {}

  @Override
  public void onPostUpdateCommitFailed(PostUpdateEvent event) {}

  @Override
  public void onPostDeleteCommitFailed(PostDeleteEvent event) {}

  private static boolean published(EntityPersister persister, Object[] state) {
    return state == null
        || state[Arrays.asList(persister.getPropertyNames()).indexOf("status")]
            == PostStatus.PUBLISHED;
  }

  private static boolean changesMoreThanCounters(EntityPersister persister, int[] dirty) {
    if (dirty == null) {
      return true;
    }
    String[] names = persister.getPropertyNames();
    return Arrays.stream(dirty).anyMatch(index -> !COUNTERS.contains(names[index]));
  }
}
