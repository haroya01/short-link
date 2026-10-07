package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class RemoteReportsTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String ALICE = "https://m.example/users/alice";

  @Mock private RemoteNotes remoteNotes;
  @Mock private RemoteActorRepository remoteActors;
  @Mock private DeliveryQueue deliveries;

  private RemoteReports reports() {
    return new RemoteReports(
        remoteNotes,
        remoteActors,
        deliveries,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JSON);
  }

  private static RemoteActorEntity alice() {
    RemoteActorEntity alice =
        new RemoteActorEntity(
            new RemoteActorDocument(
                ALICE,
                ALICE + "#main-key",
                "pem",
                ALICE + "/inbox",
                "https://m.example/inbox",
                "alice",
                "m.example",
                null,
                null,
                null),
            Instant.parse("2026-10-07T00:00:00Z"));
    ReflectionTestUtils.setField(alice, "id", 42L);
    return alice;
  }

  @Test
  void aForwardedReportIsAFlagFromThisServerNamingTheAccountAndTheNote() {
    when(remoteNotes.target(9L))
        .thenReturn(Optional.of(new RemoteNotes.Target("https://m.example/s/1", 42L, true)));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(alice()));

    reports().forward(9L, "광고 계정");

    ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(isNull(), id.capture(), body.capture(), eq(List.of("https://m.example/inbox")));
    JsonNode flag = JSON.readTree(body.getValue());
    assertThat(flag.path("type").asString()).isEqualTo("Flag");
    assertThat(flag.path("id").asString())
        .isEqualTo(id.getValue())
        .startsWith("https://kurl.me/ap/instance#flags/");
    assertThat(flag.path("actor").asString()).isEqualTo("https://kurl.me/ap/instance");
    assertThat(flag.path("content").asString()).isEqualTo("광고 계정");
    assertThat(flag.path("object").toString())
        .isEqualTo("[\"" + ALICE + "\",\"https://m.example/s/1\"]");
  }

  @Test
  void aSuspendedServerIsNotSentReports() {
    RemoteActorEntity alice = alice();
    ReflectionTestUtils.setField(alice, "serverBlock", "SUSPEND");
    when(remoteNotes.target(9L))
        .thenReturn(Optional.of(new RemoteNotes.Target("https://m.example/s/1", 42L, true)));
    when(remoteActors.findById(42L)).thenReturn(Optional.of(alice));

    reports().forward(9L, "광고 계정");

    verifyNoInteractions(deliveries);
  }

  @Test
  void aMembersNoteIsNotForwardedAnywhere() {
    when(remoteNotes.target(9L)).thenReturn(Optional.empty());

    reports().forward(9L, null);

    verifyNoInteractions(remoteActors, deliveries);
  }
}
