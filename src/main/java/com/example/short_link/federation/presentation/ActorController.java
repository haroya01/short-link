package com.example.short_link.federation.presentation;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.federation.application.ActorDocument;
import com.example.short_link.federation.application.ActorDocuments;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.presentation.response.OrderedCollectionResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(FederationUrls.ACTOR_PATH + "{publicId}")
@RequiredArgsConstructor
public class ActorController {

  private final FederationActorService actors;
  private final FederationUrls urls;
  private final NoteSnapshotReader notes;
  private final ActorDocuments documents;

  @GetMapping
  public ResponseEntity<ActorDocument> actor(
      @PathVariable String publicId,
      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
    return actors
        .byPublicId(publicId)
        .map(
            actor ->
                ActivityPubMedia.wantsHtml(accept)
                    ? ResponseEntity.status(302)
                        .location(URI.create(urls.profile(actor.user().username())))
                        .<ActorDocument>build()
                    : ResponseEntity.ok()
                        .contentType(ActivityPubMedia.ACTIVITY_JSON)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                        .body(documents.person(actor)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/followers")
  public ResponseEntity<OrderedCollectionResponse> followers(@PathVariable String publicId) {
    return hiddenCount(publicId, urls.followers(publicId));
  }

  @GetMapping("/following")
  public ResponseEntity<OrderedCollectionResponse> following(@PathVariable String publicId) {
    return hiddenCount(publicId, urls.following(publicId));
  }

  @GetMapping("/outbox")
  public ResponseEntity<OrderedCollectionResponse> outbox(@PathVariable String publicId) {
    return actors
        .byPublicId(publicId)
        .map(
            actor ->
                collection(
                    new OrderedCollectionResponse(
                        ActivityPubMedia.CONTEXT,
                        urls.outbox(publicId),
                        "OrderedCollection",
                        Math.toIntExact(notes.countForFollowersByAuthor(actor.user().id())),
                        List.of())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  // Pinned notes, newest pin first, as Mastodon reads them for the profile. Items are note ids;
  // the remote fetches each one it does not know yet.
  @GetMapping("/featured")
  public ResponseEntity<OrderedCollectionResponse> featured(@PathVariable String publicId) {
    return actors
        .byPublicId(publicId)
        .map(
            actor -> {
              List<Object> items =
                  notes.pinnedIds(actor.user().id()).stream().<Object>map(urls::note).toList();
              return collection(
                  new OrderedCollectionResponse(
                      ActivityPubMedia.CONTEXT,
                      urls.featured(publicId),
                      "OrderedCollection",
                      items.size(),
                      items));
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private ResponseEntity<OrderedCollectionResponse> hiddenCount(String publicId, String id) {
    return actors
        .byPublicId(publicId)
        .map(
            actor ->
                collection(
                    new OrderedCollectionResponse(
                        ActivityPubMedia.CONTEXT, id, "OrderedCollection", null, null)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private static ResponseEntity<OrderedCollectionResponse> collection(
      OrderedCollectionResponse body) {
    return ResponseEntity.ok().contentType(ActivityPubMedia.ACTIVITY_JSON).body(body);
  }
}
