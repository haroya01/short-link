package com.example.short_link.federation.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@KurlWebMvcTest(
    controllers = {WebFingerController.class, ActorController.class, NodeInfoController.class})
@Import({FederationUrls.class, FederationControllersTest.Props.class})
class FederationControllersTest {

  @TestConfiguration
  static class Props {
    @Bean
    FederationProperties federationProperties() {
      return new FederationProperties("https://kurl.me", "https://blog.kurl.me");
    }
  }

  private static final LocalActor YUKI =
      new LocalActor(
          new FederationUser(
              7L,
              "yuki",
              "first line\n<b>second</b>",
              "https://cdn/a.png",
              "유키 · 백엔드",
              List.of(new FederationUser.ProfileLink("x", "https://x.com/yuki?a=1&b=2"))),
          "pid123",
          "-----BEGIN PUBLIC KEY-----\nAAA\n-----END PUBLIC KEY-----\n");

  @Autowired private MockMvc mvc;
  @MockitoBean private FederationActorService actors;
  @MockitoBean private NoteSnapshotReader notes;

  @Test
  void webFingerAnswersAcctWithActorAndProfileLinks() throws Exception {
    when(actors.byUsername("yuki")).thenReturn(Optional.of(YUKI));

    mvc.perform(get("/.well-known/webfinger").param("resource", "acct:yuki@kurl.me"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/jrd+json"))
        .andExpect(jsonPath("$.subject").value("acct:yuki@kurl.me"))
        .andExpect(jsonPath("$.aliases[0]").value("https://kurl.me/ap/actors/pid123"))
        .andExpect(jsonPath("$.links[0].rel").value("self"))
        .andExpect(jsonPath("$.links[0].type").value("application/activity+json"))
        .andExpect(jsonPath("$.links[0].href").value("https://kurl.me/ap/actors/pid123"))
        .andExpect(jsonPath("$.links[1].href").value("https://blog.kurl.me/@yuki"));
  }

  @Test
  void webFingerByActorUrlAndMissesAre404() throws Exception {
    when(actors.byPublicId("pid123")).thenReturn(Optional.of(YUKI));
    mvc.perform(get("/.well-known/webfinger").param("resource", "https://kurl.me/ap/actors/pid123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("acct:yuki@kurl.me"));

    when(actors.byUsername("ghost")).thenReturn(Optional.empty());
    mvc.perform(get("/.well-known/webfinger").param("resource", "acct:ghost@kurl.me"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/.well-known/webfinger").param("resource", "acct:yuki@mastodon.social"))
        .andExpect(status().isNotFound());
  }

  @Test
  void webFingerAnswersForTheInstanceActorSoItsSignaturesVerify() throws Exception {
    mvc.perform(get("/.well-known/webfinger").param("resource", "acct:kurl.me@kurl.me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("acct:kurl.me@kurl.me"))
        .andExpect(jsonPath("$.links[0].rel").value("self"))
        .andExpect(jsonPath("$.links[0].type").value("application/activity+json"))
        .andExpect(jsonPath("$.links[0].href").value("https://kurl.me/ap/instance"));
    mvc.perform(get("/.well-known/webfinger").param("resource", "https://kurl.me/ap/instance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subject").value("acct:kurl.me@kurl.me"));
  }

  @Test
  void aLockedAccountTellsOtherServersItApprovesFollowersByHand() throws Exception {
    when(actors.byPublicId("pid456"))
        .thenReturn(
            Optional.of(
                new LocalActor(
                    new FederationUser(8L, "mio", null, null, null, List.of(), true),
                    "pid456",
                    YUKI.publicKeyPem())));

    mvc.perform(get("/ap/actors/pid456").header("Accept", "application/activity+json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.manuallyApprovesFollowers").value(true));
  }

  @Test
  void actorDocumentCarriesWhatMastodonNeeds() throws Exception {
    when(actors.byPublicId("pid123")).thenReturn(Optional.of(YUKI));

    mvc.perform(
            get("/ap/actors/pid123")
                .header("Accept", "application/activity+json, application/ld+json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
        .andExpect(jsonPath("$['@context'][0]").value("https://www.w3.org/ns/activitystreams"))
        .andExpect(jsonPath("$['@context'][1]").value("https://w3id.org/security/v1"))
        .andExpect(jsonPath("$.id").value("https://kurl.me/ap/actors/pid123"))
        .andExpect(jsonPath("$.type").value("Person"))
        .andExpect(jsonPath("$.preferredUsername").value("yuki"))
        .andExpect(jsonPath("$.name").value("유키 · 백엔드"))
        .andExpect(jsonPath("$.summary").value("<p>first line<br>&lt;b&gt;second&lt;/b&gt;</p>"))
        .andExpect(jsonPath("$.url").value("https://blog.kurl.me/@yuki"))
        .andExpect(jsonPath("$.inbox").value("https://kurl.me/ap/actors/pid123/inbox"))
        .andExpect(jsonPath("$.endpoints.sharedInbox").value("https://kurl.me/ap/inbox"))
        .andExpect(jsonPath("$.manuallyApprovesFollowers").value(false))
        .andExpect(jsonPath("$.icon.url").value("https://cdn/a.png"))
        .andExpect(jsonPath("$['@context'][2].PropertyValue").value("schema:PropertyValue"))
        .andExpect(jsonPath("$.attachment[0].type").value("PropertyValue"))
        .andExpect(jsonPath("$.attachment[0].name").value("X"))
        .andExpect(
            jsonPath("$.attachment[0].value")
                .value(
                    "<a href=\"https://x.com/yuki?a=1&amp;b=2\" target=\"_blank\""
                        + " rel=\"nofollow noopener noreferrer me\" translate=\"no\">"
                        + "x.com/yuki?a=1&amp;b=2</a>"))
        .andExpect(jsonPath("$.publicKey.id").value("https://kurl.me/ap/actors/pid123#main-key"))
        .andExpect(jsonPath("$.publicKey.owner").value("https://kurl.me/ap/actors/pid123"))
        .andExpect(jsonPath("$.publicKey.publicKeyPem").value(YUKI.publicKeyPem()));
  }

  @Test
  void browsersAreSentToTheProfileAndOtherClientsGetJson() throws Exception {
    when(actors.byPublicId("pid123")).thenReturn(Optional.of(YUKI));

    mvc.perform(get("/ap/actors/pid123").header("Accept", "text/html,application/xhtml+xml,*/*"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://blog.kurl.me/@yuki"));
    mvc.perform(get("/ap/actors/pid123").header("Accept", "*/*"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/activity+json"));
    mvc.perform(get("/ap/actors/pid123").header("Accept", "not a media type"))
        .andExpect(status().isOk());
    mvc.perform(get("/ap/actors/pid123")).andExpect(status().isOk());
  }

  @Test
  void collectionsHideFollowerCountsAndUnknownActorsAre404() throws Exception {
    when(actors.byPublicId("pid123")).thenReturn(Optional.of(YUKI));
    when(actors.byPublicId("nope")).thenReturn(Optional.empty());

    mvc.perform(get("/ap/actors/pid123/followers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("OrderedCollection"))
        .andExpect(jsonPath("$.totalItems").doesNotExist());
    mvc.perform(get("/ap/actors/pid123/following"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("https://kurl.me/ap/actors/pid123/following"))
        .andExpect(jsonPath("$.totalItems").doesNotExist());
    when(notes.countByAuthor(7L)).thenReturn(3L);
    mvc.perform(get("/ap/actors/pid123/outbox"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(3));
    mvc.perform(get("/ap/actors/nope")).andExpect(status().isNotFound());
    mvc.perform(get("/ap/actors/nope/followers")).andExpect(status().isNotFound());
    mvc.perform(get("/ap/actors/nope/outbox")).andExpect(status().isNotFound());
  }

  @Test
  void mastodonsAcceptHeaderGetsTheActivityDocument() throws Exception {
    when(actors.byPublicId("pid123")).thenReturn(Optional.of(YUKI));

    mvc.perform(
            get("/ap/actors/pid123")
                .header(
                    "Accept",
                    "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\""))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/activity+json"))
        .andExpect(jsonPath("$.type").value("Person"));
  }

  @Test
  void hostMetaPointsLegacyClientsAtWebFinger() throws Exception {
    mvc.perform(get("/.well-known/host-meta"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/xrd+xml"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString(
                        "template=\"https://kurl.me/.well-known/webfinger?resource={uri}\"")));
  }

  @Test
  void nodeInfoDiscoveryPointsAtTheDocument() throws Exception {
    mvc.perform(get("/.well-known/nodeinfo"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.links[0].rel").value("http://nodeinfo.diaspora.software/ns/schema/2.1"))
        .andExpect(jsonPath("$.links[0].href").value("https://kurl.me/ap/nodeinfo/2.1"));
    mvc.perform(get("/ap/nodeinfo/2.1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value("2.1"))
        .andExpect(jsonPath("$.software.name").value("kurl"))
        .andExpect(jsonPath("$.protocols[0]").value("activitypub"))
        .andExpect(jsonPath("$.openRegistrations").value(true));
  }
}
