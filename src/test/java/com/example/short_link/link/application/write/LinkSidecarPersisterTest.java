package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.profilebinding.domain.LinkProfileBindingEntity;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LinkSidecarPersisterTest {

  @Test
  void persistsAllSidecarsThroughEntityManager() {
    EntityManager entityManager = mock(EntityManager.class);
    LinkSidecarPersister persister = new LinkSidecarPersister(entityManager);
    LinkEntity link = withId(new LinkEntity("https://example.com", "abc1234"), 123L);

    persister.persistAll(link);

    ArgumentCaptor<Object> sidecars = ArgumentCaptor.forClass(Object.class);
    verify(entityManager, times(4)).persist(sidecars.capture());
    assertThat(sidecars.getAllValues())
        .extracting(Object::getClass)
        .containsExactly(
            LinkOgMetadataEntity.class,
            LinkAccessControlEntity.class,
            LinkProfileBindingEntity.class,
            LinkExpirationPolicyEntity.class);
    assertThat(sidecars.getAllValues())
        .extracting(LinkSidecarPersisterTest::linkId)
        .containsOnly(123L);
  }

  private static Long linkId(Object sidecar) {
    return switch (sidecar) {
      case LinkOgMetadataEntity e -> e.linkId().value();
      case LinkAccessControlEntity e -> e.linkId().value();
      case LinkProfileBindingEntity e -> e.linkId().value();
      case LinkExpirationPolicyEntity e -> e.linkId().value();
      default -> throw new IllegalArgumentException("unexpected sidecar: " + sidecar);
    };
  }

  private static LinkEntity withId(LinkEntity entity, Long id) {
    try {
      Field field = LinkEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
      return entity;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
