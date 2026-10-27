package com.example.short_link.user.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.user.SilenceCheck;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MuteSilenceSqlTextTest {

  private final List<String> statements = new ArrayList<>();

  private MuteRepositoryAdapter adapter() {
    EntityManager em = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(em.createNativeQuery(anyString()))
        .thenAnswer(
            invocation -> {
              statements.add(invocation.getArgument(0));
              return query;
            });
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getSingleResult()).thenReturn(0);
    MuteRepositoryAdapter adapter = new MuteRepositoryAdapter(mock(JpaMuteRepository.class));
    ReflectionTestUtils.setField(adapter, "em", em);
    return adapter;
  }

  @Test
  void theSilenceCheckSendsTheSameStatementForEachKindOfActor() throws Exception {
    MuteRepositoryAdapter adapter = adapter();
    Instant now = Instant.parse("2026-10-27T00:00:00Z");

    adapter.silences(SilenceCheck.of(9L, Actor.member(2L)), now);
    adapter.silences(SilenceCheck.of(9L, Actor.remote(7L)), now);
    adapter.silences(SilenceCheck.of(9L, Actor.member(2L)).inConversation(40L), now);

    Files.createDirectories(Path.of("build/silence-sql"));
    Files.write(Path.of("build/silence-sql/mute-silences.sql"), statements);
    assertThat(statements)
        .containsExactlyElementsOf(
            Files.readAllLines(Path.of("src/test/resources/silence-sql/mute-silences.sql")));
  }
}
