package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.common.federation.CustomEmoji;
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
  void mapsRowsToActorsAndToleratesNullAvatarAndName() {
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList())
        .thenReturn(
            List.of(
                new Object[] {1L, "alice", "a.png", "Alice"},
                new Object[] {2L, "bob", null, null}));

    Map<Long, NotificationActor> resolved = adapter().resolve(Set.of(1L, 2L));

    assertThat(resolved.get(1L))
        .isEqualTo(new NotificationActor(1L, "alice", "a.png", null, null, "Alice", List.of()));
    assertThat(resolved.get(2L)).isEqualTo(new NotificationActor(2L, "bob", null));
  }

  @Test
  void remoteRowsCarryTheHandleNameAndTheEmojisThatDrawIt() {
    when(em.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList())
        .thenReturn(
            List.of(
                new Object[] {
                  42L,
                  "ged",
                  "m.example",
                  "https://files.m.example/ged.png",
                  null,
                  "https://m.example/users/ged",
                  "Ged :verified:",
                  "[{\"shortcode\":\"verified\",\"url\":\"https://files.m.example/v.png\"}]"
                },
                new Object[] {
                  43L, null, "relay.example", null, null, "https://relay.example/actor", null, null
                }));

    Map<Long, NotificationActor> resolved = adapter().resolveRemote(Set.of(42L, 43L));

    assertThat(resolved.get(42L))
        .isEqualTo(
            new NotificationActor(
                null,
                "ged@m.example",
                "https://files.m.example/ged.png",
                "https://m.example/users/ged",
                42L,
                "Ged :verified:",
                List.of(new CustomEmoji("verified", "https://files.m.example/v.png"))));
    assertThat(resolved.get(43L))
        .isEqualTo(
            new NotificationActor(
                null, "relay.example", null, "https://relay.example/actor", 43L, null, List.of()));
  }
}
