package com.example.short_link.federation.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.common.note.NoteSnapshotReader.Image;
import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.NoteSnapshotReader.Poll;
import com.example.short_link.common.note.NoteSnapshotReader.PollOption;
import com.example.short_link.common.note.NoteSnapshotReader.Quote;
import com.example.short_link.common.note.NoteSnapshotReader.QuotedNote;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationSettings;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.LocalActor;
import com.example.short_link.federation.application.NoteAddressing;
import com.example.short_link.federation.application.NoteDocuments;
import com.example.short_link.federation.application.NoteFederation;
import com.example.short_link.federation.application.RemoteAccountFinder;
import com.example.short_link.federation.application.RemoteParents;
import com.example.short_link.federation.application.delivery.DeliveryQueue;
import com.example.short_link.federation.domain.FederationActorEntity;
import com.example.short_link.federation.domain.FederationUser;
import com.example.short_link.federation.domain.RemoteActorDocument;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.FederationActorRepository;
import com.example.short_link.federation.domain.repository.FederationFollowerRepository;
import com.example.short_link.testsupport.KurlWebMvcTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@KurlWebMvcTest(controllers = {NoteObjectController.class, ActorController.class})
@Import({
  FederationUrls.class,
  NoteDocuments.class,
  NoteAddressing.class,
  OutgoingDocumentsGoldenTest.Props.class
})
class OutgoingDocumentsGoldenTest {

  @TestConfiguration
  static class Props {
    @Bean
    FederationProperties federationProperties() {
      return new FederationProperties("https://kurl.me", "https://blog.kurl.me");
    }
  }

  private static final Path GOLDEN = Path.of("src/test/resources/federation-golden");
  private static final Instant CREATED = Instant.parse("2026-10-06T00:00:00.123456Z");
  private static final Instant EDITED = Instant.parse("2026-10-06T01:02:03.456Z");
  private static final List<String> FOLLOWER_INBOXES =
      List.of("https://a.example/inbox", "https://b.example/inbox");
  private static final String PEM = "-----BEGIN PUBLIC KEY-----\nAAA\n-----END PUBLIC KEY-----\n";

  private static final LocalActor YUKI =
      new LocalActor(new FederationUser(7L, "yuki", null, null), "pid", PEM);
  private static final LocalActor MINA =
      new LocalActor(new FederationUser(8L, "mina", null, null), "rp", PEM);

  private static final RemoteParents.Parent ALICE_POST =
      new RemoteParents.Parent(
          "https://mastodon.example/users/alice/statuses/1",
          "https://mastodon.example/users/alice",
          "@alice@mastodon.example",
          "https://mastodon.example/inbox",
          true);
  private static final RemoteParents.Parent CAROL_POST =
      new RemoteParents.Parent(
          "https://mastodon.example/users/carol/statuses/9",
          "https://mastodon.example/users/carol",
          "@carol@mastodon.example",
          "https://mastodon.example/inbox",
          true);

  private static final NoteSnapshot PUBLIC =
      draft(42L, "첫 줄 https://example.com/a?b=1&c=2.\n둘째 줄 #kurl #연합\n\n새 문단 <b>그대로</b> & \"따옴표\"")
          .language("ko")
          .snapshot();
  private static final NoteSnapshot UNLISTED =
      draft(43L, "조용히 올린 글").visibility(NoteVisibility.UNLISTED).snapshot();
  private static final NoteSnapshot PRIVATE =
      draft(44L, "팔로워만 보는 글").visibility(NoteVisibility.PRIVATE).contentWarning("스포일러").snapshot();
  private static final NoteSnapshot DIRECT_REPLY =
      draft(45L, "@alice@mastodon.example 둘이서만")
          .inReplyTo(900L)
          .visibility(NoteVisibility.DIRECT)
          .snapshot();
  private static final NoteSnapshot REMOTE_REPLY =
      draft(46L, "@alice@mastodon.example 공개 답장").inReplyTo(900L).snapshot();
  private static final NoteSnapshot LOCAL_REPLY = draft(47L, "회원 글에 답장").inReplyTo(42L).snapshot();
  private static final NoteSnapshot POLL =
      draft(48L, "점심 뭐 먹지")
          .poll(
              new Poll(
                  List.of(new PollOption("국수", 3), new PollOption("김밥", 1)),
                  Instant.parse("2026-10-07T00:00:00.500Z"),
                  false,
                  4,
                  false))
          .snapshot();
  private static final NoteSnapshot POLL_ENDED =
      draft(49L, "주말에 할 것")
          .poll(
              new Poll(
                  List.of(new PollOption("등산", 2), new PollOption("독서", 5)),
                  Instant.parse("2026-10-07T00:00:00.500Z"),
                  true,
                  6,
                  true))
          .snapshot();
  private static final NoteSnapshot LOCAL_QUOTE =
      draft(50L, "이 글 좋다")
          .quotedNote(new QuotedNote(41L, "mina"))
          .quote(new Quote("블로그 <제목> & 부제", "my-post", "yuki"))
          .snapshot();
  private static final NoteSnapshot REMOTE_QUOTE =
      draft(51L, "다른 서버 글 인용")
          .quotedNote(
              new QuotedNote(
                  77L,
                  "carol",
                  "https://mastodon.example/users/carol/statuses/9",
                  "https://mastodon.example/@carol/9"))
          .snapshot();
  private static final NoteSnapshot REMOTE_MENTION =
      draft(52L, "@bob@misskey.example 안녕하세요 #인사").snapshot();
  private static final NoteSnapshot IMAGES =
      draft(53L, "사진 두 장")
          .images(
              new Image("https://cdn.kurl.me/n/53/a.webp", "image/webp", "창가의 고양이", 1200, 800),
              new Image("https://cdn.kurl.me/n/53/b.png", "image/png", null, null, null))
          .sensitive()
          .snapshot();
  private static final NoteSnapshot EDITED_NOTE = draft(54L, "고친 글").edited(EDITED).snapshot();
  private static final NoteSnapshot EDITED_MENTION =
      draft(55L, "@bob@misskey.example 고쳐서 다시").edited(EDITED).snapshot();

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private NoteDocuments documents;
  @MockitoBean private NoteSnapshotReader notes;
  @MockitoBean private FederationActorService localActors;
  @MockitoBean private RemoteParents remoteParents;
  @MockitoBean private RemoteAccountFinder finder;
  @Autowired private NoteAddressing addressing;

  private final FederationSettings settings = mock(FederationSettings.class);
  private final FederationActorRepository actors = mock(FederationActorRepository.class);
  private final FederationFollowerRepository followers = mock(FederationFollowerRepository.class);
  private final DeliveryQueue deliveries = mock(DeliveryQueue.class);

  private NoteFederation federation;

  @BeforeEach
  void yukiAndMinaFederateAndHaveFollowersElsewhere() {
    federation =
        new NoteFederation(
            notes,
            remoteParents,
            addressing,
            localActors,
            settings,
            actors,
            followers,
            documents,
            deliveries,
            json,
            Clock.fixed(Instant.parse("2026-10-06T02:00:00.789Z"), ZoneOffset.UTC),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    for (LocalActor actor : List.of(YUKI, MINA)) {
      Long userId = actor.user().id();
      when(localActors.byUserId(userId)).thenReturn(Optional.of(actor));
      when(localActors.byUsername(actor.user().username())).thenReturn(Optional.of(actor));
      when(localActors.byPublicId(actor.publicId())).thenReturn(Optional.of(actor));
      when(actors.findByUserId(userId))
          .thenReturn(Optional.of(new FederationActorEntity(userId, actor.publicId(), PEM, "enc")));
      when(settings.isEnabled(userId)).thenReturn(true);
      when(followers.deliveryInboxes(userId)).thenReturn(FOLLOWER_INBOXES);
    }
    when(remoteParents.of(900L)).thenReturn(Optional.of(ALICE_POST));
    when(remoteParents.of(77L)).thenReturn(Optional.of(CAROL_POST));
    when(remoteParents.of(42L)).thenReturn(Optional.empty());
    RemoteActorEntity alice =
        remote(
            "https://mastodon.example/users/alice",
            "alice",
            "mastodon.example",
            "https://mastodon.example/@alice");
    RemoteActorEntity bob =
        remote("https://misskey.example/users/9bob", "bob", "misskey.example", null);
    when(finder.find("alice@mastodon.example")).thenReturn(Optional.of(alice));
    when(finder.find("bob@misskey.example")).thenReturn(Optional.of(bob));
    when(finder.known("alice@mastodon.example")).thenReturn(Optional.of(alice));
    when(finder.known("bob@misskey.example")).thenReturn(Optional.of(bob));
    for (NoteSnapshot note :
        List.of(
            PUBLIC,
            UNLISTED,
            PRIVATE,
            DIRECT_REPLY,
            REMOTE_REPLY,
            LOCAL_REPLY,
            POLL,
            POLL_ENDED,
            LOCAL_QUOTE,
            REMOTE_QUOTE,
            REMOTE_MENTION,
            IMAGES,
            EDITED_NOTE,
            EDITED_MENTION)) {
      when(notes.find(note.id())).thenReturn(Optional.of(note));
    }
  }

  @Test
  void createsGoingToFollowersAreByteForByteAsRecorded() throws Exception {
    assertGolden("create-public", delivered(FOLLOWER_INBOXES, () -> federation.created(42L, 7L)));
    assertGolden("create-unlisted", delivered(FOLLOWER_INBOXES, () -> federation.created(43L, 7L)));
    assertGolden("create-private", delivered(FOLLOWER_INBOXES, () -> federation.created(44L, 7L)));
    assertGolden(
        "create-local-reply", delivered(FOLLOWER_INBOXES, () -> federation.created(47L, 7L)));
    assertGolden(
        "create-poll",
        delivered(FOLLOWER_INBOXES, () -> federation.created(48L, 7L)),
        "object/oneOf/*/replies");
    assertGolden(
        "create-local-quote", delivered(FOLLOWER_INBOXES, () -> federation.created(50L, 7L)));
    assertGolden("create-images", delivered(FOLLOWER_INBOXES, () -> federation.created(53L, 7L)));
  }

  @Test
  void createsReachingAnotherServerAreByteForByteAsRecorded() throws Exception {
    assertGolden(
        "create-direct-reply",
        delivered(
            List.of("https://mastodon.example/inbox"), () -> federation.createdElsewhere(45L, 7L)));
    assertGolden(
        "create-remote-reply",
        delivered(
            List.of(
                "https://a.example/inbox",
                "https://b.example/inbox",
                "https://mastodon.example/inbox"),
            () -> federation.createdElsewhere(46L, 7L)));
    assertGolden(
        "create-remote-quote",
        delivered(
            List.of(
                "https://a.example/inbox",
                "https://b.example/inbox",
                "https://mastodon.example/inbox"),
            () -> federation.createdElsewhere(51L, 7L)));
    assertGolden(
        "create-remote-mention",
        delivered(
            List.of(
                "https://a.example/inbox",
                "https://b.example/inbox",
                "https://misskey.example/inbox"),
            () -> federation.createdElsewhere(52L, 7L)));
  }

  @Test
  void updatesAndTheEndOfAPollAreByteForByteAsRecorded() throws Exception {
    assertGolden(
        "update-edited", delivered(FOLLOWER_INBOXES, () -> federation.edited(54L, 7L, false)));
    assertGolden(
        "update-remote-mention",
        delivered(
            List.of(
                "https://a.example/inbox",
                "https://b.example/inbox",
                "https://misskey.example/inbox"),
            () -> federation.edited(55L, 7L, true)));
    assertGolden(
        "update-poll-ended",
        delivered(FOLLOWER_INBOXES, () -> federation.pollEnded(49L, 7L)),
        "object/anyOf/*/replies");
  }

  @Test
  void deletesAreByteForByteAsRecorded() throws Exception {
    assertGolden(
        "delete",
        delivered(FOLLOWER_INBOXES, () -> federation.deleted(42L, 7L, null, List.of(), null)),
        "object");
    assertGolden(
        "delete-remote-mention",
        delivered(
            List.of(
                "https://a.example/inbox",
                "https://b.example/inbox",
                "https://misskey.example/inbox"),
            () -> federation.deleted(52L, 7L, null, List.of("bob@misskey.example"), null)),
        "object");
  }

  @Test
  void repostsAndLikesAreByteForByteAsRecorded() throws Exception {
    assertGolden("announce", delivered(FOLLOWER_INBOXES, () -> federation.reposted(300L, 42L, 8L)));
    assertGolden(
        "undo-announce", delivered(FOLLOWER_INBOXES, () -> federation.unreposted(300L, 42L, 8L)));
    List<String> followersAndAlice =
        List.of(
            "https://a.example/inbox", "https://b.example/inbox", "https://mastodon.example/inbox");
    assertGolden(
        "announce-remote",
        delivered(followersAndAlice, () -> federation.repostedRemote(301L, 900L, 8L, true)));
    assertGolden(
        "undo-announce-remote",
        delivered(followersAndAlice, () -> federation.repostedRemote(301L, 900L, 8L, false)));
    List<String> alice = List.of("https://mastodon.example/inbox");
    assertGolden("like", delivered(alice, () -> federation.likedRemote(900L, 8L, true)));
    assertGolden("undo-like", delivered(alice, () -> federation.likedRemote(900L, 8L, false)));
  }

  @Test
  void notesFetchedByOtherServersAreByteForByteAsRecorded() throws Exception {
    assertGolden("note-public", fetched("/ap/notes/42"));
    assertGolden("note-unlisted", fetched("/ap/notes/43"));
    assertGolden("note-remote-reply", fetched("/ap/notes/46"));
    assertGolden("note-local-reply", fetched("/ap/notes/47"));
    assertGolden("note-poll", fetched("/ap/notes/48"), "oneOf/*/replies");
    assertGolden("note-poll-ended", fetched("/ap/notes/49"), "anyOf/*/replies");
    assertGolden("note-local-quote", fetched("/ap/notes/50"));
    assertGolden("note-remote-quote", fetched("/ap/notes/51"));
    assertGolden("note-remote-mention", fetched("/ap/notes/52"));
    assertGolden("note-images", fetched("/ap/notes/53"));
    assertGolden("note-edited", fetched("/ap/notes/54"));
  }

  @Test
  void aNoteIsFetchedAsItWasDeliveredEvenWhenItAddressesSomeoneElsewhere() throws Exception {
    List<String> followersAndMastodon =
        List.of(
            "https://a.example/inbox", "https://b.example/inbox", "https://mastodon.example/inbox");
    List<String> followersAndMisskey =
        List.of(
            "https://a.example/inbox", "https://b.example/inbox", "https://misskey.example/inbox");
    Map<Long, String> sent = new LinkedHashMap<>();
    for (long id : List.of(42L, 43L, 47L, 48L, 50L, 53L)) {
      sent.put(id, delivered(FOLLOWER_INBOXES, () -> federation.created(id, 7L)));
    }
    sent.put(46L, delivered(followersAndMastodon, () -> federation.createdElsewhere(46L, 7L)));
    sent.put(51L, delivered(followersAndMastodon, () -> federation.createdElsewhere(51L, 7L)));
    sent.put(52L, delivered(followersAndMisskey, () -> federation.createdElsewhere(52L, 7L)));
    sent.put(54L, delivered(FOLLOWER_INBOXES, () -> federation.edited(54L, 7L, false)));
    sent.put(55L, delivered(followersAndMisskey, () -> federation.edited(55L, 7L, true)));

    for (var delivery : sent.entrySet()) {
      ObjectNode fetched = (ObjectNode) json.readTree(fetched("/ap/notes/" + delivery.getKey()));
      fetched.remove("@context");
      assertThat(fetched)
          .as("note %d", delivery.getKey())
          .isEqualTo(json.readTree(delivery.getValue()).get("object"));
    }
  }

  @Test
  void theActorDocumentIsByteForByteAsRecorded() throws Exception {
    when(localActors.byPublicId("full"))
        .thenReturn(
            Optional.of(
                new LocalActor(
                    new FederationUser(
                        9L,
                        "haroya",
                        "첫 줄 <b>굵게</b> & 그대로\n둘째 줄",
                        "https://cdn.kurl.me/a/9.png",
                        "하로야 · 백엔드",
                        List.of(
                            new FederationUser.ProfileLink("x", "https://x.com/haroya?a=1&b=2"),
                            new FederationUser.ProfileLink("github", "https://github.com/haroya/")),
                        true),
                    "full",
                    PEM)));

    assertGolden("actor-full", fetched("/ap/actors/full"), "@context/2", "@context/2/featured");
    assertGolden("actor-plain", fetched("/ap/actors/pid"), "@context/2", "@context/2/featured");
  }

  private String delivered(List<String> inboxes, Runnable send) {
    clearInvocations(deliveries);
    send.run();
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(deliveries).enqueue(any(), any(), body.capture(), eq(inboxes));
    return body.getValue();
  }

  private String fetched(String path) throws Exception {
    return mvc.perform(get(path).header("Accept", "application/activity+json"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  // Map.of iterates in an order that changes from one JVM start to the next, so the objects the
  // documents build with it are compared with their keys sorted; every other byte is compared.
  private void assertGolden(String name, String actual, String... unordered) throws Exception {
    JsonNode tree = json.readTree(actual);
    assertThat(json.writeValueAsString(tree)).isEqualTo(actual);
    for (String path : unordered) {
      sortKeys(tree, Arrays.asList(path.split("/")), path);
    }
    String canonical = json.writeValueAsString(tree) + "\n";
    Path file = GOLDEN.resolve(name + ".json");
    assertThat(canonical)
        .as(
            "the %s document sent to other servers changed; replace %s only when that is meant:%n%s",
            name, file, canonical)
        .isEqualTo(Files.readString(file));
  }

  private static void sortKeys(JsonNode node, List<String> path, String whole) {
    assertThat(node).as("no node at %s", whole).isNotNull();
    if (path.isEmpty()) {
      assertThat(node.isObject()).as("%s is not an object", whole).isTrue();
      ObjectNode object = (ObjectNode) node;
      Map<String, JsonNode> sorted = new TreeMap<>();
      object.properties().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
      object.removeAll();
      object.setAll(sorted);
      return;
    }
    String step = path.get(0);
    List<String> rest = path.subList(1, path.size());
    if (step.equals("*")) {
      assertThat(node.isEmpty()).as("nothing under %s", whole).isFalse();
      node.forEach(child -> sortKeys(child, rest, whole));
    } else if (node.isArray()) {
      sortKeys(node.get(Integer.parseInt(step)), rest, whole);
    } else {
      sortKeys(node.get(step), rest, whole);
    }
  }

  private static RemoteActorEntity remote(
      String actorUri, String username, String domain, String profileUrl) {
    String host = "https://" + domain;
    return new RemoteActorEntity(
        new RemoteActorDocument(
            actorUri,
            actorUri + "#main-key",
            PEM,
            actorUri + "/inbox",
            host + "/inbox",
            username,
            domain,
            profileUrl,
            null,
            null,
            false,
            false),
        CREATED);
  }

  private static Draft draft(Long id, String body) {
    return new Draft(id, body);
  }

  private static final class Draft {
    private final Long id;
    private final String body;
    private Instant editedAt;
    private Long inReplyToId;
    private Quote quote;
    private List<Image> images = List.of();
    private QuotedNote quotedNote;
    private String contentWarning;
    private boolean sensitive;
    private NoteVisibility visibility = NoteVisibility.PUBLIC;
    private Poll poll;
    private String language;

    private Draft(Long id, String body) {
      this.id = id;
      this.body = body;
    }

    Draft edited(Instant at) {
      editedAt = at;
      return this;
    }

    Draft inReplyTo(Long noteId) {
      inReplyToId = noteId;
      return this;
    }

    Draft quote(Quote post) {
      quote = post;
      return this;
    }

    Draft images(Image... attached) {
      images = List.of(attached);
      return this;
    }

    Draft quotedNote(QuotedNote quoted) {
      quotedNote = quoted;
      return this;
    }

    Draft contentWarning(String warning) {
      contentWarning = warning;
      sensitive = true;
      return this;
    }

    Draft sensitive() {
      sensitive = true;
      return this;
    }

    Draft visibility(NoteVisibility chosen) {
      visibility = chosen;
      return this;
    }

    Draft poll(Poll attached) {
      poll = attached;
      return this;
    }

    Draft language(String tag) {
      language = tag;
      return this;
    }

    NoteSnapshot snapshot() {
      return new NoteSnapshot(
          id,
          7L,
          "yuki",
          body,
          CREATED,
          editedAt,
          inReplyToId,
          quote,
          images,
          quotedNote,
          contentWarning,
          sensitive,
          visibility,
          poll,
          language);
    }
  }
}
