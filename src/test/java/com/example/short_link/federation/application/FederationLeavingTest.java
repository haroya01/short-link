package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FederationLeavingTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private FederationActorRepository actors;
  @Mock private FederationFollowerRepository followers;
  @Mock private RemoteFollowing following;
  @Mock private DeliveryQueue deliveries;

  private FederationLeaving leaving() {
    return new FederationLeaving(
        actors,
        followers,
        following,
        deliveries,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JSON,
        Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC));
  }

  @Test
  void followersServersAreToldToDropTheAccountAndFollowersAreForgotten() {
    when(actors.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(followers.deliveryInboxes(7L)).thenReturn(List.of("https://a.example/inbox"));

    leaving().leave(7L);

    verify(following).leave(7L, "https://kurl.me/ap/actors/pid");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries)
        .enqueue(
            eq(7L),
            eq("https://kurl.me/ap/actors/pid#delete/5000"),
            body.capture(),
            eq(List.of("https://a.example/inbox")));
    JsonNode delete = JSON.readTree(body.getValue());
    assertThat(delete.path("type").asString()).isEqualTo("Delete");
    assertThat(delete.path("object").asString()).isEqualTo("https://kurl.me/ap/actors/pid");
    verify(followers).deleteAllForUser(7L);
  }

  @Test
  void noFollowersMeansNothingToSendAndNoActorMeansNothingToDo() {
    when(actors.findByUserId(7L))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "pid", "PUB", "enc")));
    when(followers.deliveryInboxes(7L)).thenReturn(List.of());
    leaving().leave(7L);
    verify(followers).deleteAllForUser(7L);

    when(actors.findByUserId(8L)).thenReturn(Optional.empty());
    leaving().leave(8L);
    verify(followers, never()).deliveryInboxes(8L);
    verify(deliveries, never())
        .enqueue(eq(7L), anyString(), anyString(), org.mockito.ArgumentMatchers.anyList());
  }
}
