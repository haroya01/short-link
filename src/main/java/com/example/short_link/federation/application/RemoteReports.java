package com.example.short_link.federation.application;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// Mastodon's forwarded report: a Flag from the instance actor, never the reporter, naming the
// account and the note, carrying the reporter's comment.
@Service
@RequiredArgsConstructor
public class RemoteReports {

  private final RemoteNotes remoteNotes;
  private final RemoteActorRepository remoteActors;
  private final DeliveryQueue deliveries;
  private final FederationUrls urls;
  private final JsonMapper json;

  @Transactional
  public void forward(Long noteId, String comment) {
    remoteNotes
        .target(noteId)
        .flatMap(
            note ->
                remoteActors
                    .findById(note.remoteActorId())
                    .map(
                        actor -> {
                          String id = urls.instance() + "#flags/" + UUID.randomUUID();
                          Map<String, Object> flag = new LinkedHashMap<>();
                          flag.put("@context", ActivityStreams.CONTEXT);
                          flag.put("id", id);
                          flag.put("type", "Flag");
                          flag.put("actor", urls.instance());
                          flag.put("content", comment == null ? "" : comment);
                          flag.put("object", List.of(actor.getActorUri(), note.uri()));
                          deliveries.enqueue(
                              null,
                              id,
                              json.writeValueAsString(flag),
                              List.of(actor.deliveryInbox()));
                          return id;
                        }));
  }
}
