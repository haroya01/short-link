package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.WebFingerResource;
import com.example.short_link.federation.presentation.response.WebFingerResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequiredArgsConstructor
public class WebFingerController {

  static final MediaType JRD = MediaType.parseMediaType("application/jrd+json");
  static final MediaType XRD = MediaType.parseMediaType("application/xrd+xml");

  private final FederationActorService actors;
  private final FederationUrls urls;

  @GetMapping("/.well-known/webfinger")
  public ResponseEntity<WebFingerResponse> webFinger(@RequestParam String resource) {
    Optional<WebFingerResponse> document =
        WebFingerResource.parse(resource, urls.domain(), urls.actor(""), urls.instance())
            .flatMap(
                parsed ->
                    switch (parsed) {
                      case WebFingerResource.Username name ->
                          actors.byUsername(name.value()).map(this::document);
                      case WebFingerResource.ActorId id ->
                          actors.byPublicId(id.publicId()).map(this::document);
                      case WebFingerResource.Instance instance -> Optional.of(instanceDocument());
                    });
    return document
        .map(
            found ->
                ResponseEntity.ok()
                    .contentType(JRD)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .body(found))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/.well-known/host-meta")
  public ResponseEntity<String> hostMeta() {
    String template = urls.webFingerTemplate();
    return ResponseEntity.ok()
        .contentType(XRD)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<XRD xmlns=\"http://docs.oasis-open.org/ns/xri/xrd-1.0\">\n"
                + "  <Link rel=\"lrdd\" template=\""
                + HtmlUtils.htmlEscape(template)
                + "\"/>\n"
                + "</XRD>\n");
  }

  private WebFingerResponse instanceDocument() {
    String actorId = urls.instance();
    return new WebFingerResponse(
        "acct:" + urls.domain() + "@" + urls.domain(),
        List.of(actorId),
        List.of(new WebFingerResponse.Link("self", ActivityPubMedia.ACTIVITY_JSON_VALUE, actorId)));
  }

  private WebFingerResponse document(LocalActor actor) {
    String actorId = urls.actor(actor.publicId());
    String profile = urls.profile(actor.user().username());
    return new WebFingerResponse(
        "acct:" + actor.user().username() + "@" + urls.domain(),
        List.of(actorId, profile),
        List.of(
            new WebFingerResponse.Link("self", ActivityPubMedia.ACTIVITY_JSON_VALUE, actorId),
            new WebFingerResponse.Link(
                "http://webfinger.net/rel/profile-page", MediaType.TEXT_HTML_VALUE, profile)));
  }
}
