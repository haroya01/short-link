package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FollowerSilenceSqlTextTest {

  private final List<String> statements = new ArrayList<>();

  private NotificationFollowerReaderAdapter adapter() {
    EntityManager em = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(em.createNativeQuery(anyString()))
        .thenAnswer(
            invocation -> {
              statements.add(invocation.getArgument(0));
              return query;
            });
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());
    NotificationFollowerReaderAdapter adapter = new NotificationFollowerReaderAdapter();
    ReflectionTestUtils.setField(adapter, "em", em);
    return adapter;
  }

  @Test
  void whoHearsOfNewWritingAndEditsIsAskedWithTheSameStatements() throws Exception {
    NotificationFollowerReaderAdapter adapter = adapter();

    adapter.followerIdsOf(7L);
    adapter.noteSubscribersOf(7L);
    adapter.noteSharersOf(5L, 7L, null);
    adapter.noteSharersOf(5L, null, 40L);

    Files.createDirectories(Path.of("build/silence-sql"));
    Files.write(Path.of("build/silence-sql/follower-silences.sql"), statements);
    assertThat(statements)
        .containsExactlyElementsOf(
            Files.readAllLines(Path.of("src/test/resources/silence-sql/follower-silences.sql")));
  }
}
