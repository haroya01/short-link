package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig(NoteFederationTransactionTest.Config.class)
class NoteFederationTransactionTest {

  @Configuration
  @EnableTransactionManagement
  @Import({NoteFederation.class, NoteDocuments.class, FederationUrls.class})
  static class Config {
    @Bean
    FederationProperties federationProperties() {
      return new FederationProperties("https://kurl.me", "https://blog.kurl.me");
    }

    @Bean
    NumberedTransactions transactionManager() {
      return new NumberedTransactions();
    }

    @Bean
    TransactionTemplate transactionTemplate(NumberedTransactions transactions) {
      return new TransactionTemplate(transactions);
    }

    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-06T02:00:00Z"), ZoneOffset.UTC);
    }
  }

  static final class NumberedTransactions extends AbstractPlatformTransactionManager {
    private int begun;
    private int open;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      open = ++begun;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
      open = 0;
    }

    void reset() {
      begun = 0;
      open = 0;
    }
  }

  private static final LocalActor YUKI =
      new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", "PUB");
  private static final RemoteParents.Parent CAROL_POST =
      new RemoteParents.Parent(
          "https://mastodon.example/users/carol/statuses/9",
          "https://mastodon.example/users/carol",
          "@carol@mastodon.example",
          "https://mastodon.example/inbox",
          true);

  @Autowired private NoteFederation federation;
  @Autowired private NumberedTransactions transactions;
  @MockitoBean private NoteSnapshotReader notes;
  @MockitoBean private RemoteParents remoteParents;
  @MockitoBean private RemoteAccountFinder finder;
  @MockitoBean private FederationActorService localActors;
  @MockitoBean private FederationSettings settings;
  @MockitoBean private FederationActorRepository actors;
  @MockitoBean private FederationFollowerRepository followers;
  @MockitoBean private DeliveryQueue deliveries;

  private final List<String> calls = new ArrayList<>();

  @BeforeEach
  void aNoteNamingBobAndQuotingCarol() {
    NoteSnapshot note =
        new NoteSnapshot(
            52L,
            7L,
            "yuki",
            "@bob@misskey.example 안녕하세요",
            Instant.parse("2026-10-06T00:00:00Z"),
            null,
            null,
            null,
            List.of(),
            new QuotedNote(
                77L,
                "carol",
                "https://mastodon.example/users/carol/statuses/9",
                "https://mastodon.example/@carol/9"),
            null,
            false,
            NoteVisibility.PUBLIC,
            null,
            null);
    transactions.reset();
    when(localActors.byUserId(7L)).thenReturn(Optional.of(YUKI));
    when(notes.find(52L))
        .thenAnswer(
            asked -> {
              called("note");
              return Optional.of(note);
            });
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"));
    when(remoteParents.of(77L))
        .thenAnswer(
            asked -> {
              called("quoted note");
              return Optional.of(CAROL_POST);
            });
    when(finder.find(anyString()))
        .thenAnswer(
            asked -> {
              called(asked.getArgument(0));
              return Optional.of(bob());
            });
    when(deliveries.enqueue(any(), any(), any(), any()))
        .thenAnswer(
            queued -> {
              called("queue");
              return 1;
            });
  }

  private void called(String what) {
    calls.add(
        what
            + (TransactionSynchronizationManager.isActualTransactionActive()
                ? " in transaction " + transactions.open
                : " outside a transaction"));
  }

  private static RemoteActorEntity bob() {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            "https://misskey.example/users/9bob",
            "https://misskey.example/users/9bob#main-key",
            "PUB",
            "https://misskey.example/users/9bob/inbox",
            "https://misskey.example/inbox",
            "bob",
            "misskey.example",
            null,
            null,
            null,
            false,
            false),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  @Test
  void aNewNoteLooksUpWhomItNamesBetweenReadingAndQueueing() {
    federation.createdElsewhere(52L, 7L);

    assertThat(calls)
        .containsExactly(
            "note in transaction 1",
            "quoted note in transaction 1",
            "bob@misskey.example outside a transaction",
            "queue in transaction 2");
  }

  @Test
  void anEditLooksUpWhomItNamesBetweenReadingAndQueueing() {
    federation.edited(52L, 7L, true);

    assertThat(calls)
        .containsExactly(
            "note in transaction 1",
            "quoted note in transaction 1",
            "bob@misskey.example outside a transaction",
            "queue in transaction 2");
  }

  @Test
  void aDeleteLooksUpWhomTheNoteNamedBeforeQueueing() {
    federation.deleted(52L, 7L, null, List.of("bob@misskey.example"), 77L);

    assertThat(calls)
        .containsExactly(
            "quoted note outside a transaction",
            "bob@misskey.example outside a transaction",
            "queue in transaction 1");
  }
}
