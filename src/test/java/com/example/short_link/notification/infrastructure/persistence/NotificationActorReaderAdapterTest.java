package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.NotificationActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationActorReaderAdapterTest {

  @Mock private EntityManager em;
  @Mock private Query query;

  private NotificationActorReaderAdapter adapter() {
    NotificationActorReaderAdapter adapter = new NotificationActorReaderAdapter();
    ReflectionTestUtils.setField(adapter, "em", em);
    return adapter;
  }

  @Test
  void emptyInputShortCircuitsWithoutQuery() {
    assertThat(adapter().resolve(Set.of())).isEmpty();
    assertThat(adapter().resolve(null)).isEmpty();
  }

  @Test
  void mapsRowsToActorsAndToleratesNullAvatar() {
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList())
        .thenReturn(List.of(new Object[] {1L, "alice", "a.png"}, new Object[] {2L, "bob", null}));

    Map<Long, NotificationActor> resolved = adapter().resolve(Set.of(1L, 2L));

    assertThat(resolved.get(1L)).isEqualTo(new NotificationActor(1L, "alice", "a.png"));
    assertThat(resolved.get(2L)).isEqualTo(new NotificationActor(2L, "bob", null));
  }
}
