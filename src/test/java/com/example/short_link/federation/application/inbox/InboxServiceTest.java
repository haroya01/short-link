package com.example.short_link.federation.application.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.RemoteNoteReactions;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationFollowers;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

  private static final String ALICE = "https://mastodon.example/users/alice";
  private static final String OWNER_URI = "https://kurl.me/ap/actors/owner1";
  private static final LocalActor OWNER =
      new LocalActor(new FederationUser(7L, "haroya", null, null), "owner1", "pem");

  @Mock private InboxVerifier verifier;
  @Mock private FederationActorService localActors;
  @Mock private FederationActorRepository actorRows;
  @Mock private FederationFollowers followers;
  @Mock private NoteSnapshotReader notes;
  @Mock private RemoteNoteReactions reactions;

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  private InboxService service() {
    return new InboxService(
        verifier,
        localActors,
        actorRows,
        followers,
        notes,
        reactions,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        JsonMapper.builder().build(),
        meters);
  }

  private static RemoteActorEntity remote(String actorUri, String domain) {
    return new RemoteActorEntity(
        new RemoteActorDocument(
            actorUri,
            actorUri + "#main-key",
            "pem",
            actorUri + "/inbox",
            null,
            "alice",
            domain,
            null,
            null,
            null),
        Instant.parse("2026-10-06T00:00:00Z"));
  }

  private static final RemoteActorEntity ALICE_ACTOR = remote(ALICE, "mastodon.example");

  static {
    ReflectionTestUtils.setField(ALICE_ACTOR, "id", 42L);
  }

  private static final String NOTE_URI = "https://kurl.me/ap/notes/1";

  private void noteOneFederates() {
    when(notes.find(1L))
        .thenReturn(
            Optional.of(
                new NoteSnapshotReader.NoteSnapshot(
                    1L,
                    7L,
                    "haroya",
                    "hello",
                    Instant.parse("2026-10-06T00:00:00Z"),
                    null,
                    null,
                    null,
                    List.of())));
    when(localActors.byUsername("haroya")).thenReturn(Optional.of(OWNER));
  }

  private static String activity(String id, String type, String object) {
    return """
        {"id":"%s","type":"%s","actor":"%s","object":%s}"""
        .formatted(id, type, ALICE, object);
  }

  private static InboxMessage request(String json) {
    return new InboxMessage("/ap/inbox", null, Map.of(), json.getBytes(StandardCharsets.UTF_8));
  }

  private static String follow(String id, String object) {
    return """
        {"@context":"https://www.w3.org/ns/activitystreams","id":"%s","type":"Follow",
         "actor":"%s","object":"%s"}"""
        .formatted(id, ALICE, object);
  }

  private void verifiedAs(RemoteActorEntity actor) {
    when(verifier.verify(any(), anyBoolean())).thenReturn(new InboxVerifier.Result.Verified(actor));
  }

  @Test
  void aPersonalInboxOfNoOneIs404() {
    when(localActors.byPublicId("ghost")).thenReturn(Optional.empty());

    assertThat(service().receive(request("{}"), "ghost").kind())
        .isEqualTo(InboxOutcome.Kind.NOT_FOUND);
    verifyNoInteractions(verifier);
  }

  @Test
  void bodiesThatAreNotActivitiesAreMalformed() {
    InboxService service = service();
    assertThat(service.receive(request("not json"), null))
        .isEqualTo(InboxOutcome.malformed("json"));
    assertThat(service.receive(request("[1]"), null)).isEqualTo(InboxOutcome.malformed("json"));
    assertThat(service.receive(request("{\"type\":\"Follow\",\"actor\":\"" + ALICE + "\"}"), null))
        .isEqualTo(InboxOutcome.malformed("fields"));
    assertThat(
            service.receive(
                request("{\"id\":\" \",\"type\":\"Follow\",\"actor\":{\"id\":7}}"), null))
        .isEqualTo(InboxOutcome.malformed("fields"));
    verifyNoInteractions(verifier);
  }

  @Test
  void activitiesWeDoNotHandleAreAcknowledgedWithoutAKeyFetch() {
    InboxService service = service();
    for (String type : new String[] {"EmojiReact", "Create", "Update", "Accept", "Block"}) {
      var outcome =
          service.receive(
              request(
                  """
                  {"id":"https://mastodon.example/a/1","type":"%s","actor":"%s",
                   "object":"https://kurl.me/ap/notes/1"}"""
                      .formatted(type, ALICE)),
              null);
      assertThat(outcome).isEqualTo(InboxOutcome.ignored("unsupported"));
    }
    verifyNoInteractions(verifier, followers);
    assertThat(
            meters
                .get("federation.inbox")
                .tags("outcome", "ignored", "reason", "unsupported")
                .counter()
                .count())
        .isEqualTo(5);
  }

  @Test
  void aFollowOfSomeoneWhoIsNotHereIsIgnored() {
    InboxService service = service();
    assertThat(
            service.receive(
                request(follow("https://mastodon.example/f/1", "https://other.example/u/x")), null))
        .isEqualTo(InboxOutcome.ignored("unknown-target"));
    when(localActors.byPublicId("missing")).thenReturn(Optional.empty());
    assertThat(
            service.receive(
                request(
                    follow("https://mastodon.example/f/1", "https://kurl.me/ap/actors/missing")),
                null))
        .isEqualTo(InboxOutcome.ignored("unknown-target"));
    verifyNoInteractions(verifier, followers);
  }

  @Test
  void aFollowToThePersonalInboxReusesItsOwner() {
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    verifiedAs(ALICE_ACTOR);

    var outcome =
        service().receive(request(follow("https://mastodon.example/f/1", OWNER_URI)), "owner1");

    assertThat(outcome).isEqualTo(InboxOutcome.accepted("follow"));
    verify(followers).follow(OWNER, ALICE_ACTOR, "https://mastodon.example/f/1");
    verify(localActors).byPublicId("owner1");
    verify(verifier).verify(any(), eq(false));
  }

  @Test
  void aFollowToTheSharedInboxResolvesItsTarget() {
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    verifiedAs(ALICE_ACTOR);

    assertThat(service().receive(request(follow("https://mastodon.example/f/1", OWNER_URI)), null))
        .isEqualTo(InboxOutcome.accepted("follow"));
    verify(followers).follow(OWNER, ALICE_ACTOR, "https://mastodon.example/f/1");
  }

  @Test
  void aFollowToAnotherInboxOwnerLooksUpTheRealTarget() {
    LocalActor other = new LocalActor(new FederationUser(8L, "other", null, null), "other1", "p");
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    when(localActors.byPublicId("other1")).thenReturn(Optional.of(other));
    verifiedAs(ALICE_ACTOR);

    service()
        .receive(
            request(follow("https://mastodon.example/f/1", "https://kurl.me/ap/actors/other1")),
            "owner1");

    verify(followers).follow(other, ALICE_ACTOR, "https://mastodon.example/f/1");
  }

  @Test
  void aBadSignatureIsUnauthorized() {
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    when(verifier.verify(any(), anyBoolean()))
        .thenReturn(new InboxVerifier.Result.Rejected("digest"));

    assertThat(service().receive(request(follow("https://mastodon.example/f/1", OWNER_URI)), null))
        .isEqualTo(InboxOutcome.unauthorized("digest"));
    verifyNoInteractions(followers);
  }

  @Test
  void aKeyFromAnotherActorCannotSpeakForTheActivityActor() {
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    verifiedAs(remote("https://mastodon.example/users/mallory", "mastodon.example"));

    assertThat(service().receive(request(follow("https://mastodon.example/f/1", OWNER_URI)), null))
        .isEqualTo(InboxOutcome.ignored("actor-mismatch"));
    verifyNoInteractions(followers);
  }

  @Test
  void anActivityIdOnAnotherHostIsNotTrusted() {
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    verifiedAs(ALICE_ACTOR);

    assertThat(service().receive(request(follow("https://evil.example/f/1", OWNER_URI)), null))
        .isEqualTo(InboxOutcome.ignored("foreign-id"));
    verifyNoInteractions(followers);
  }

  @Test
  void undoOfAnEmbeddedFollowRemovesThatFollower() {
    when(actorRows.findByPublicId("owner1"))
        .thenReturn(Optional.of(new FederationActorEntity(7L, "owner1", "pem", "cipher")));
    verifiedAs(ALICE_ACTOR);

    var outcome =
        service()
            .receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/f/1","type":"Follow",
                               "actor":"%s","object":"%s"}}"""
                        .formatted(ALICE, ALICE, OWNER_URI)),
                null);

    assertThat(outcome).isEqualTo(InboxOutcome.accepted("undo-follow"));
    verify(followers).unfollow(7L, ALICE_ACTOR);
  }

  @Test
  void undoByFollowIdFallsBackWhenTheTargetIsUnknown() {
    verifiedAs(ALICE_ACTOR);
    InboxService service = service();

    service.receive(
        request(
            """
            {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s",
             "object":"https://mastodon.example/f/1"}"""
                .formatted(ALICE)),
        null);
    when(actorRows.findByPublicId("gone1")).thenReturn(Optional.empty());
    service.receive(
        request(
            """
            {"id":"https://mastodon.example/u/2","type":"Undo","actor":"%s",
             "object":{"id":"https://mastodon.example/f/2","type":"Follow",
                       "actor":"%s","object":"https://kurl.me/ap/actors/gone1"}}"""
                .formatted(ALICE, ALICE)),
        null);

    verify(followers).unfollow(ALICE_ACTOR, "https://mastodon.example/f/1");
    verify(reactions).removeByActivity(42L, "https://mastodon.example/f/1");
    verify(followers).unfollow(ALICE_ACTOR, "https://mastodon.example/f/2");
    verify(followers, never()).unfollow(any(Long.class), any());
  }

  @Test
  void undoOfAnythingWeNeverRecordedIsLeftAlone() {
    InboxService service = service();
    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/b/1","type":"Block","actor":"%s"}}"""
                        .formatted(ALICE, ALICE)),
                null))
        .isEqualTo(InboxOutcome.ignored("unsupported"));
    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/l/1","type":"Like",
                               "actor":"https://mastodon.example/users/bob"}}"""
                        .formatted(ALICE)),
                null))
        .isEqualTo(InboxOutcome.ignored("actor-mismatch"));
    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/f/1","type":"Follow",
                               "actor":"https://mastodon.example/users/bob"}}"""
                        .formatted(ALICE)),
                null))
        .isEqualTo(InboxOutcome.ignored("actor-mismatch"));
    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/1","type":"Undo","actor":"%s"}"""
                        .formatted(ALICE)),
                null))
        .isEqualTo(InboxOutcome.malformed("fields"));
    verifyNoInteractions(verifier, followers, reactions);
  }

  @Test
  void aLikeOrABoostOfAFederatedNoteIsRecordedAgainstTheVerifiedActor() {
    noteOneFederates();
    when(localActors.byPublicId("owner1")).thenReturn(Optional.of(OWNER));
    verifiedAs(ALICE_ACTOR);
    InboxService service = service();

    assertThat(
            service.receive(
                request(
                    activity(
                        "https://mastodon.example/users/alice#likes/9",
                        "Like",
                        "\"" + NOTE_URI + "\"")),
                null))
        .isEqualTo(InboxOutcome.accepted("like"));
    assertThat(
            service.receive(
                request(
                    activity(
                        "https://mastodon.example/users/alice/statuses/5/activity",
                        "Announce",
                        "{\"id\":\"" + NOTE_URI + "\",\"type\":\"Note\"}")),
                "owner1"))
        .isEqualTo(InboxOutcome.accepted("announce"));

    verify(reactions).add(1L, 42L, Kind.LIKE, "https://mastodon.example/users/alice#likes/9");
    verify(localActors).byUsername("haroya");
    verify(reactions)
        .add(1L, 42L, Kind.ANNOUNCE, "https://mastodon.example/users/alice/statuses/5/activity");
  }

  @Test
  void reactionsToAnythingButAFederatedNoteAreIgnoredWithoutAKeyFetch() {
    InboxService service = service();
    when(notes.find(2L)).thenReturn(Optional.empty());
    when(notes.find(3L))
        .thenReturn(
            Optional.of(
                new NoteSnapshotReader.NoteSnapshot(
                    3L, 8L, "quiet", "x", Instant.EPOCH, null, null, null, List.of())));
    when(localActors.byUsername("quiet")).thenReturn(Optional.empty());

    for (String object :
        new String[] {
          "\"https://mastodon.example/statuses/1\"",
          "\"https://kurl.me/ap/notes/abc\"",
          "\"https://kurl.me/ap/notes/2\"",
          "\"https://kurl.me/ap/notes/3\"",
          "null"
        }) {
      assertThat(
              service.receive(
                  request(activity("https://mastodon.example/l/1", "Like", object)), null))
          .isEqualTo(InboxOutcome.ignored("unknown-target"));
    }
    verifyNoInteractions(verifier, reactions);
  }

  @Test
  void undoOfALikeOrABoostRemovesItByNoteOrByActivityId() {
    verifiedAs(ALICE_ACTOR);
    InboxService service = service();

    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/3","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/users/alice#likes/9","type":"Like",
                               "actor":"%s","object":"%s"}}"""
                        .formatted(ALICE, ALICE, NOTE_URI)),
                null))
        .isEqualTo(InboxOutcome.accepted("undo-like"));
    assertThat(
            service.receive(
                request(
                    """
                    {"id":"https://mastodon.example/u/4","type":"Undo","actor":"%s",
                     "object":{"id":"https://mastodon.example/s/5/activity","type":"Announce",
                               "actor":"%s"}}"""
                        .formatted(ALICE, ALICE)),
                null))
        .isEqualTo(InboxOutcome.accepted("undo-announce"));

    verify(reactions).remove(1L, 42L, Kind.LIKE);
    verify(reactions).removeByActivity(42L, "https://mastodon.example/s/5/activity");
    verifyNoInteractions(followers);
  }

  @Test
  void anAccountDeletingItselfIsForgottenUsingOnlyTheCachedKey() {
    when(verifier.verify(any(), eq(true)))
        .thenReturn(new InboxVerifier.Result.Verified(ALICE_ACTOR));

    var outcome =
        service()
            .receive(
                request(
                    """
                    {"id":"%s#delete","type":"Delete","actor":"%s","object":"%s"}"""
                        .formatted(ALICE, ALICE, ALICE)),
                null);

    assertThat(outcome).isEqualTo(InboxOutcome.accepted("delete-actor"));
    verify(followers).forget(ALICE_ACTOR);
  }

  @Test
  void aDeleteFromAnAccountWeNeverKnewIsAcknowledged() {
    when(verifier.verify(any(), anyBoolean()))
        .thenReturn(new InboxVerifier.Result.Rejected("unknown-key"));
    String delete =
        """
        {"id":"%s#delete","type":"Delete","actor":"%s","object":{"id":"%s","type":"Tombstone"}}"""
            .formatted(ALICE, ALICE, ALICE);

    assertThat(service().receive(request(delete), null))
        .isEqualTo(InboxOutcome.ignored("gone-actor"));
    when(verifier.verify(any(), anyBoolean()))
        .thenReturn(new InboxVerifier.Result.Rejected("signature-mismatch"));
    assertThat(service().receive(request(delete), null))
        .isEqualTo(InboxOutcome.unauthorized("signature-mismatch"));
    verifyNoInteractions(followers);
  }

  @Test
  void deletesOfOtherObjectsAreNotHandledYet() {
    assertThat(
            service()
                .receive(
                    request(
                        """
                        {"id":"https://mastodon.example/d/1","type":"Delete","actor":"%s",
                         "object":"https://mastodon.example/statuses/1"}"""
                            .formatted(ALICE)),
                    null))
        .isEqualTo(InboxOutcome.ignored("unsupported"));
    verifyNoInteractions(verifier);
  }
}
