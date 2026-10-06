package com.example.short_link.federation.presentation;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.presentation.response.ActorResponse;
import com.example.short_link.federation.presentation.response.OrderedCollectionResponse;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping(FederationUrls.ACTOR_PATH + "{publicId}")
@RequiredArgsConstructor
public class ActorController {

  private static final List<Object> ACTOR_CONTEXT =
      List.of(
          ActivityPubMedia.CONTEXT,
          "https://w3id.org/security/v1",
          Map.of(
              "manuallyApprovesFollowers", "as:manuallyApprovesFollowers",
              "toot", "http://joinmastodon.org/ns#",
              "discoverable", "toot:discoverable"));

  private final FederationActorService actors;
  private final FederationUrls urls;
  private final NoteSnapshotReader notes;

  @GetMapping
  public ResponseEntity<ActorResponse> actor(
      @PathVariable String publicId,
      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
    return actors
        .byPublicId(publicId)
        .map(
            actor ->
                ActivityPubMedia.wantsHtml(accept)
                    ? ResponseEntity.status(302)
                        .location(URI.create(urls.profile(actor.user().username())))
                        .<ActorResponse>build()
                    : ResponseEntity.ok()
                        .contentType(ActivityPubMedia.ACTIVITY_JSON)
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                        .body(document(actor)))
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
                        Math.toIntExact(notes.countByAuthor(actor.user().id())),
                        List.of())))
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

  private ActorResponse document(LocalActor actor) {
    String id = urls.actor(actor.publicId());
    String username = actor.user().username();
    String avatar = actor.user().avatarUrl();
    return new ActorResponse(
        ACTOR_CONTEXT,
        id,
        "Person",
        username,
        username,
        summary(actor.user().bio()),
        urls.profile(username),
        urls.inbox(actor.publicId()),
        urls.outbox(actor.publicId()),
        urls.followers(actor.publicId()),
        urls.following(actor.publicId()),
        new ActorResponse.Endpoints(urls.sharedInbox()),
        false,
        true,
        avatar == null || avatar.isBlank() ? null : new ActorResponse.Image("Image", avatar),
        new ActorResponse.PublicKey(urls.key(actor.publicId()), id, actor.publicKeyPem()));
  }

  static String summary(String bio) {
    if (bio == null || bio.isBlank()) {
      return null;
    }
    return "<p>" + HtmlUtils.htmlEscape(bio.strip()).replace("\n", "<br>") + "</p>";
  }
}
