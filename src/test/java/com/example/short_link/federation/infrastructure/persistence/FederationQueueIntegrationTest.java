package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.federation.domain.FederationDeliveryEntity;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationDeliveryRepository;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

// Proves the SKIP LOCKED claim and the cleanup query run on real MySQL; Hibernate renders the lock
// hint per dialect, which a mock cannot show.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FederationQueueIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");

  @Autowired private FederationDeliveryRepository deliveries;
  @Autowired private RemoteActorRepository remoteActors;
  @Autowired private EntityManager em;

  private FederationDeliveryEntity row(String key, Instant due) {
    return deliveries.saveAndFlush(
        new FederationDeliveryEntity(
            key, "https://a.example/inbox", "a.example", null, "act-" + key, "{}", due));
  }

  @Test
  void lockDueReturnsOnlyPendingRowsThatAreDueOldestFirst() {
    FederationDeliveryEntity later = row("later-" + System.nanoTime(), NOW.minusSeconds(10));
    FederationDeliveryEntity earlier = row("earlier-" + System.nanoTime(), NOW.minusSeconds(60));
    row("future-" + System.nanoTime(), NOW.plusSeconds(60));
    FederationDeliveryEntity done = row("done-" + System.nanoTime(), NOW.minusSeconds(120));
    done.delivered(202);
    deliveries.saveAndFlush(done);

    List<FederationDeliveryEntity> due = deliveries.lockDue(NOW, 10);

    assertThat(due)
        .extracting(FederationDeliveryEntity::getId)
        .containsSubsequence(earlier.getId(), later.getId())
        .doesNotContain(done.getId());
    assertThat(deliveries.lockDue(NOW, 1)).hasSize(1);
    assertThat(deliveries.existsByDedupeKey(earlier.getDedupeKey())).isTrue();
  }

  @Test
  void purgeDeletesOnlyFinishedRowsOlderThanTheCutoff() {
    FederationDeliveryEntity done = row("old-done-" + System.nanoTime(), NOW);
    done.giveUp(410, "HTTP 410");
    FederationDeliveryEntity pending = row("old-pending-" + System.nanoTime(), NOW);
    deliveries.saveAndFlush(done);
    em.createNativeQuery("UPDATE federation_delivery SET updated_at = ? WHERE id IN (?, ?)")
        .setParameter(1, NOW.minus(Duration.ofDays(30)))
        .setParameter(2, done.getId())
        .setParameter(3, pending.getId())
        .executeUpdate();
    em.clear();

    deliveries.deleteFinishedBefore(NOW.minus(Duration.ofDays(7)));

    assertThat(deliveries.findById(done.getId())).isEmpty();
    assertThat(deliveries.findById(pending.getId())).isPresent();
  }

  @Test
  void remoteActorsAreFoundByUriAndKey() {
    String uri = "https://mastodon.example/users/it-" + System.nanoTime();
    RemoteActorEntity saved =
        remoteActors.saveAndFlush(
            new RemoteActorEntity(
                new RemoteActorDocument(
                    uri,
                    uri + "#main-key",
                    "pem",
                    uri + "/inbox",
                    null,
                    "it",
                    "mastodon.example",
                    null,
                    null,
                    null),
                NOW));
    ReflectionTestUtils.getField(saved, "id");

    assertThat(remoteActors.findByActorUri(uri)).isPresent();
    assertThat(remoteActors.findByKeyId(uri + "#main-key")).isPresent();
    assertThat(remoteActors.findByKeyId(uri + "#other")).isEmpty();
  }
}
