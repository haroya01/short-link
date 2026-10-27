package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.ActorKeys;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.inbox.InboxMessage;
import com.example.short_link.federation.application.inbox.InboxOutcome;
import com.example.short_link.federation.application.inbox.InboxService;
import com.example.short_link.federation.application.inbox.SignedInboxRequests;
import com.example.short_link.federation.application.signature.HttpSignatures;
import com.example.short_link.federation.domain.RelayMode;
import com.example.short_link.federation.scheduler.RelayWorker;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PageAsk;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "short-link.federation.relay.enabled=true")
@ActiveProfiles("test")
class RelayStoreIntegrationTest {

  private static final ActorKeys.Pem KEYS = new ActorKeys().generate();
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PUBLIC = "https://www.w3.org/ns/activitystreams#Public";

  @MockitoBean private FederationHttp http;
  @Autowired private RelaySubscriptions subscriptions;
  @Autowired private RelayIntake intake;
  @Autowired private RelayWorker worker;
  @Autowired private RelayTally tally;
  @Autowired private InboxService inbox;
  @Autowired private NoteQueryService query;
  @Autowired private FederationProperties props;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private String tag;
  private String server;
  private String alice;
  private String relay;
  private final List<Long> members = new ArrayList<>();

  @BeforeEach
  void aRelayThatAcceptedUs() {
    tag = "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    server = "https://mastodon-" + tag + ".example";
    alice = server + "/users/alice";
    relay = "https://relay-" + tag + ".example/tag/dev";
    jdbc.update(
        "INSERT INTO federation_relay (actor_uri, inbox, follow_activity_id, state, accepted_at,"
            + " created_at, updated_at) VALUES (?, ?, ?, 'ACCEPTED', NOW(6), NOW(6), NOW(6))",
        relay,
        relay,
        "https://kurl.me/ap/instance#relays/" + tag);
    subscriptions.load();
    subscriptions.change(RelayMode.STORE, List.of("ko", "ja"));
    when(http.get(eq(URI.create(alice)), any()))
        .thenReturn(ok(JSON.writeValueAsString(actorDocument())));
  }

  @AfterEach
  void removeWhatThisRunWrote() {
    jdbc.update(
        "DELETE FROM note WHERE remote_actor_id IN"
            + " (SELECT id FROM federation_remote_actor WHERE domain = ?)",
        URI.create(server).getHost());
    jdbc.update(
        "DELETE FROM federation_remote_actor WHERE domain = ?", URI.create(server).getHost());
    jdbc.update(
        "DELETE FROM federation_domain_block WHERE domain = ?", URI.create(server).getHost());
    jdbc.update("DELETE FROM federation_relay WHERE actor_uri = ?", relay);
    for (Long member : members) {
      jdbc.update("DELETE FROM users WHERE id = ?", member);
    }
    jdbc.update(
        "UPDATE federation_relay_setting SET mode = 'COUNT', languages = 'ko,ja', chosen_at = NULL");
    subscriptions.load();
  }

  private Map<String, Object> actorDocument() {
    return Map.of(
        "id",
        alice,
        "type",
        "Person",
        "preferredUsername",
        "alice",
        "inbox",
        alice + "/inbox",
        "endpoints",
        Map.of("sharedInbox", server + "/inbox"),
        "publicKey",
        Map.of("id", alice + "#main-key", "owner", alice, "publicKeyPem", KEYS.publicKey()));
  }

  private static FederationHttp.Result ok(String body) {
    return new FederationHttp.Result.Ok(
        200, body.getBytes(StandardCharsets.UTF_8), "application/activity+json; charset=utf-8");
  }

  private Map<String, Object> post(String uri, String content) {
    return Map.of(
        "id",
        uri,
        "type",
        "Note",
        "attributedTo",
        alice,
        "to",
        List.of(PUBLIC),
        "cc",
        List.of(alice + "/followers"),
        "published",
        "2026-10-17T00:00:00Z",
        "content",
        content,
        "contentMap",
        Map.of("ko", content),
        "attachment",
        List.of(
            Map.of(
                "type", "Document",
                "mediaType", "image/png",
                "url", server + "/media/a.png",
                "name", "screen")));
  }

  private String announced(String content) {
    String uri = alice + "/statuses/" + UUID.randomUUID();
    when(http.get(eq(URI.create(uri)), any(), any(Duration.class)))
        .thenReturn(ok(JSON.writeValueAsString(post(uri, content))));
    assertThat(intake.offer(uri, relay)).isEqualTo("queued");
    assertThat(worker.processNext()).isTrue();
    return uri;
  }

  private Long noteId(String uri) {
    return jdbc.queryForList("SELECT id FROM note WHERE uri = ?", Long.class, uri).stream()
        .findFirst()
        .orElse(null);
  }

  private Long member(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    Long id = users.save(user).getId();
    members.add(id);
    return id;
  }

  private List<Long> federated(Long viewer) {
    return query.federated(viewer, new PageAsk(0, 50)).items().stream().map(NoteView::id).toList();
  }

  private InboxOutcome fromAlice(Map<String, Object> activity) {
    byte[] body = JSON.writeValueAsString(activity).getBytes(StandardCharsets.UTF_8);
    return inbox.receive(
        new InboxMessage(
            "/ap/inbox",
            null,
            SignedInboxRequests.headers(
                alice + "#main-key",
                KEYS.privateKey(),
                HttpSignatures.host(URI.create(props.baseUrl())),
                "/ap/inbox",
                body,
                Instant.now()),
            body),
        null);
  }

  @Test
  void aRelayedPostIsKeptAsAnyReceivedNoteWithItsRelayAndItsPicturesLeftWhereTheyAre() {
    long before = tally.today("stored");

    Long note = noteId(announced("<p>오늘의 개발 일지 #개발</p>"));

    assertThat(note).isNotNull();
    assertThat(tally.today("stored")).isEqualTo(before + 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT r.actor_uri FROM federation_relayed_note n"
                    + " JOIN federation_relay r ON r.id = n.relay_id WHERE n.note_id = ?",
                String.class,
                note))
        .isEqualTo(relay);
    assertThat(
            jdbc.queryForMap(
                "SELECT n.visibility, n.language, a.actor_uri FROM note n"
                    + " JOIN federation_remote_actor a ON a.id = n.remote_actor_id WHERE n.id = ?",
                note))
        .containsEntry("visibility", "PUBLIC")
        .containsEntry("language", "ko")
        .containsEntry("actor_uri", alice);
    assertThat(jdbc.queryForMap("SELECT url, storage_key FROM note_media WHERE note_id = ?", note))
        .containsEntry("url", server + "/media/a.png")
        .containsEntry("storage_key", "");
    assertThat(jdbc.queryForList("SELECT tag FROM note_tag WHERE note_id = ?", String.class, note))
        .containsExactly("개발");
  }

  @Test
  void theOtherServersFeedShowsARelayedPostToEveryReaderButOneWhoHidItsAccountOrServer() {
    Long reader = member("rd");
    Long blocker = member("bk");
    Long muter = member("mt");
    Long serverBlocker = member("sb");

    Long note = noteId(announced("<p>個人開発のメモ</p>"));
    Long actor =
        jdbc.queryForObject("SELECT remote_actor_id FROM note WHERE id = ?", Long.class, note);
    jdbc.update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        blocker,
        actor);
    jdbc.update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " created_at) VALUES (?, ?, FALSE, NOW(6))",
        muter,
        actor);
    jdbc.update(
        "INSERT INTO user_domain_block (user_id, domain, created_at) VALUES (?, ?, NOW(6))",
        serverBlocker,
        URI.create(server).getHost());

    assertThat(federated(reader)).contains(note);
    assertThat(federated(blocker)).doesNotContain(note);
    assertThat(federated(muter)).doesNotContain(note);
    assertThat(federated(serverBlocker)).doesNotContain(note);

    jdbc.update(
        "INSERT INTO federation_domain_block (domain, severity, created_at)"
            + " VALUES (?, 'SUSPEND', NOW(6))",
        URI.create(server).getHost());
    assertThat(federated(reader)).doesNotContain(note);
  }

  @Test
  void itsAuthorsUpdateRevisesARelayedPostAndTheirDeleteRemovesItWithItsRelayMark() {
    String uri = announced("<p>初稿</p>");
    Long note = noteId(uri);

    Map<String, Object> edited = new java.util.LinkedHashMap<>(post(uri, "<p>改稿</p>"));
    edited.put("updated", "2026-10-17T01:00:00Z");
    assertThat(
            fromAlice(
                    Map.of(
                        "id",
                        uri + "#updates/1",
                        "type",
                        "Update",
                        "actor",
                        alice,
                        "object",
                        edited))
                .reason())
        .isEqualTo("update");
    assertThat(jdbc.queryForObject("SELECT body FROM note WHERE id = ?", String.class, note))
        .contains("改稿");

    assertThat(
            fromAlice(
                    Map.of(
                        "id",
                        uri + "#delete",
                        "type",
                        "Delete",
                        "actor",
                        alice,
                        "object",
                        Map.of("id", uri, "type", "Tombstone")))
                .reason())
        .isEqualTo("delete");
    assertThat(noteId(uri)).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM federation_relayed_note WHERE note_id = ?", Long.class, note))
        .isZero();
  }

  @Test
  void aSecondAnnounceOfAKeptPostIsNotFetchedOrKeptAgain() {
    String uri = announced("<p>개발자 모임</p>");

    assertThat(intake.offer(uri, relay)).isEqualTo("queued");
    long before = tally.today("kept-already");
    assertThat(worker.processNext()).isTrue();

    assertThat(tally.today("kept-already")).isEqualTo(before + 1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM note WHERE uri = ?", Long.class, uri))
        .isEqualTo(1);
  }
}
