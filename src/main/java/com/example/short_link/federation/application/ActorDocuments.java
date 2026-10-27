package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.FederationUser;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
@RequiredArgsConstructor
public class ActorDocuments {

  private static final boolean DISCOVERABLE = true;

  private static final List<Object> PERSON_CONTEXT =
      List.of(
          ActivityStreams.CONTEXT,
          "https://w3id.org/security/v1",
          Map.of(
              "manuallyApprovesFollowers", "as:manuallyApprovesFollowers",
              "toot", "http://joinmastodon.org/ns#",
              "discoverable", "toot:discoverable",
              "indexable", "toot:indexable",
              "featured", Map.of("@id", "toot:featured", "@type", "@id"),
              "schema", "http://schema.org#",
              "PropertyValue", "schema:PropertyValue",
              "value", "schema:value"));

  private static final Map<String, String> LINK_NAMES =
      Map.of(
          "x", "X",
          "line", "LINE",
          "threads", "Threads",
          "facebook", "Facebook",
          "kakao", "Kakao",
          "instagram", "Instagram",
          "linkedin", "LinkedIn");

  private final FederationUrls urls;

  public ActorDocument person(LocalActor actor) {
    String id = urls.actor(actor.publicId());
    String username = actor.user().username();
    String avatar = actor.user().avatarUrl();
    return new ActorDocument(
        PERSON_CONTEXT,
        id,
        "Person",
        username,
        actor.user().displayName() == null ? username : actor.user().displayName(),
        summary(actor.user().bio()),
        urls.profile(username),
        urls.inbox(actor.publicId()),
        urls.outbox(actor.publicId()),
        urls.followers(actor.publicId()),
        urls.following(actor.publicId()),
        urls.featured(actor.publicId()),
        attachment(actor.user().links()),
        new ActorDocument.Endpoints(urls.sharedInbox()),
        actor.user().locked(),
        DISCOVERABLE,
        DISCOVERABLE,
        avatar == null || avatar.isBlank() ? null : new ActorDocument.Image("Image", avatar),
        new ActorDocument.PublicKey(urls.key(actor.publicId()), id, actor.publicKeyPem()));
  }

  public ActorDocument instance(String publicKeyPem) {
    String id = urls.instance();
    return new ActorDocument(
        List.of(ActivityStreams.CONTEXT, "https://w3id.org/security/v1"),
        id,
        "Application",
        urls.domain(),
        urls.domain(),
        null,
        null,
        urls.sharedInbox(),
        id + "/outbox",
        null,
        null,
        null,
        null,
        new ActorDocument.Endpoints(urls.sharedInbox()),
        true,
        false,
        false,
        null,
        new ActorDocument.PublicKey(urls.instanceKey(), id, publicKeyPem));
  }

  private static List<ActorDocument.PropertyValue> attachment(
      List<FederationUser.ProfileLink> links) {
    if (links == null || links.isEmpty()) {
      return null;
    }
    return links.stream()
        .map(
            link ->
                new ActorDocument.PropertyValue(
                    "PropertyValue",
                    LINK_NAMES.getOrDefault(link.channel(), link.channel()),
                    anchor(link.url())))
        .toList();
  }

  private static String anchor(String url) {
    String shown = url.replaceFirst("^https?://", "").replaceFirst("/$", "");
    return "<a href=\""
        + HtmlUtils.htmlEscape(url)
        + "\" target=\"_blank\" rel=\"nofollow noopener noreferrer me\" translate=\"no\">"
        + HtmlUtils.htmlEscape(shown)
        + "</a>";
  }

  static String summary(String bio) {
    if (bio == null || bio.isBlank()) {
      return null;
    }
    return "<p>" + HtmlUtils.htmlEscape(bio.strip()).replace("\n", "<br>") + "</p>";
  }
}
