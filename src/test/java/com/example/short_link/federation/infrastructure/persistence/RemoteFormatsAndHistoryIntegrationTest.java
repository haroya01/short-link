package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.federation.RemoteHistory;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.application.RemoteFollowing;
import com.example.short_link.federation.application.RemoteNoteIntake;
import com.example.short_link.federation.application.RemoteNoteParser;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PageAsk;
import com.example.short_link.note.application.write.NotePollService;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.testsupport.FederationFixtures;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class RemoteFormatsAndHistoryIntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @MockitoBean private FederationHttp http;
  @Autowired private RemoteNoteParser parser;
  @Autowired private RemoteNoteIntake intake;
  @Autowired private RemoteHistory history;
  @Autowired private RemoteFollowing following;
  @Autowired private NoteQueryService query;
  @Autowired private NotePollService polls;
  @Autowired private RemoteActorRepository actors;
  @Autowired private FederationActorService localActors;
  @Autowired private FederationUrls urls;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("taskExecutor")
  private ThreadPoolTaskExecutor taskExecutor;

  @Autowired
  @Qualifier("webhookExecutor")
  private ThreadPoolTaskExecutor webhookExecutor;

  private String tag;
  private final List<Long> members = new ArrayList<>();
  private final List<Long> accounts = new ArrayList<>();

  @BeforeEach
  void tagThisRun() {
    tag = "h" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  @AfterEach
  void removeWhatThisRunWrote() {
    awaitBackgroundWork();
    for (Long member : members) {
      jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", member);
      jdbc.update("DELETE FROM federation_delivery WHERE signer_user_id = ?", member);
    }
    for (Long account : accounts) {
      jdbc.update("DELETE FROM federation_remote_actor WHERE id = ?", account);
    }
    for (Long member : members) {
      jdbc.update("DELETE FROM users WHERE id = ?", member);
    }
  }

  private String host(String server) {
    return server + "-" + tag + ".example";
  }

  private String fixture(String name) {
    String text = FederationFixtures.text(name);
    for (String server : List.of("mastodon", "blog", "lemmy", "peertube")) {
      text = text.replace("//" + server + ".example", "//" + host(server));
      text = text.replace("." + server + ".example", "." + host(server));
    }
    return text;
  }

  private Long member(String prefix) {
    String name = prefix + tag;
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    Long id = users.save(user).getId();
    members.add(id);
    return id;
  }

  private RemoteActorEntity account(String actorUri, String username) {
    String domain = URI.create(actorUri).getHost();
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, username,"
            + " domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        actorUri,
        actorUri + "#main-key",
        actorUri + "/inbox",
        username,
        domain);
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, actorUri);
    accounts.add(id);
    return actors.findById(id).orElseThrow();
  }

  private void follows(Long member, RemoteActorEntity account) {
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        member,
        account.getId(),
        "https://kurl.me/ap/actors/x#follows/" + UUID.randomUUID());
  }

  private void deliver(RemoteActorEntity account, String fixture) {
    JsonNode object = JSON.readTree(fixture);
    intake.keep(account, parser.parse(object, object), null);
  }

  private void serves(String uri, String body) {
    when(http.get(eq(URI.create(uri)), any()))
        .thenReturn(
            new FederationHttp.Result.Ok(
                200,
                body.getBytes(StandardCharsets.UTF_8),
                "application/activity+json; charset=utf-8"));
  }

  private long notifications(Long member) {
    awaitBackgroundWork();
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM notification WHERE recipient_user_id = ?", Long.class, member);
  }

  private void awaitBackgroundWork() {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    long idleSince = -1;
    while (System.nanoTime() < deadline) {
      boolean busy =
          List.of(taskExecutor, webhookExecutor).stream()
              .anyMatch(
                  executor ->
                      executor.getActiveCount() != 0
                          || !executor.getThreadPoolExecutor().getQueue().isEmpty());
      if (busy) {
        idleSince = -1;
      } else if (idleSince < 0) {
        idleSince = System.nanoTime();
      } else if (System.nanoTime() - idleSince >= Duration.ofMillis(100).toNanos()) {
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static NoteView byKind(NoteFeedView feed, String kind) {
    return feed.items().stream()
        .filter(note -> note.remote() != null && kind.equals(note.remote().kind()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aPollArticlePageAndVideoReachTheFollowersFeedAndTheAccountsPage() {
    Long reader = member("fr");
    RemoteActorEntity alice = account("https://" + host("mastodon") + "/users/alice", "alice");
    RemoteActorEntity blog = account("https://" + host("blog") + "/?author=1", "blog");
    RemoteActorEntity river = account("https://" + host("lemmy") + "/u/river", "river");
    RemoteActorEntity maker = account("https://" + host("peertube") + "/accounts/maker", "maker");
    for (RemoteActorEntity account : List.of(alice, blog, river, maker)) {
      follows(reader, account);
    }

    deliver(alice, fixture("mastodon-question.json"));
    deliver(blog, fixture("wordpress-article.json"));
    deliver(river, fixture("lemmy-page.json"));
    deliver(maker, fixture("peertube-video.json"));

    NoteFeedView feed = query.following(reader, new PageAsk(0, 20));
    assertThat(feed.items()).hasSize(4);

    NoteView question = byKind(feed, "QUESTION");
    assertThat(question.body()).isEqualTo("Which editor do you write in?");
    assertThat(question.remote().url())
        .isEqualTo("https://" + host("mastodon") + "/@alice/113300000000000001");
    assertThat(question.poll().options())
        .containsExactly(
            new NoteView.PollOption("Vim", 7),
            new NoteView.PollOption("Emacs", 3),
            new NoteView.PollOption("Something else", 2));
    assertThat(question.poll().votersCount()).isEqualTo(12);
    assertThat(question.poll().voted()).isTrue();
    assertThat(question.poll().expired()).isTrue();

    NoteView article = byKind(feed, "ARTICLE");
    assertThat(article.remote().title()).isEqualTo("What the garden taught me this year");
    assertThat(article.body()).isEqualTo("A season of tomatoes, slugs & patience. […]");
    assertThat(article.media()).hasSize(1);

    NoteView page = byKind(feed, "PAGE");
    assertThat(page.remote().community()).isEqualTo("gardening@" + host("lemmy"));
    assertThat(page.remote().url()).isEqualTo("https://" + host("lemmy") + "/post/12345");

    NoteView video = byKind(feed, "VIDEO");
    assertThat(video.remote().title()).isEqualTo("Building a raised bed in ten minutes");
    assertThat(video.media())
        .extracting(NoteView.Media::url)
        .containsExactly("https://" + host("peertube") + "/lazy-static/previews/9c9de5e8.jpg");

    assertThat(query.byRemoteActor(reader, maker.getId(), new PageAsk(0, 20)).items())
        .extracting(NoteView::id)
        .containsExactly(video.id());
    assertThatThrownBy(() -> polls.vote(reader, question.id(), List.of(0)))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_POLL_ELSEWHERE));
  }

  @Test
  void aSparseAccountsHistoryIsFilledOnceQuietlyAndItsBlockersStillSeeNothing() {
    Long reader = member("fv");
    Long blocker = member("fb");
    Long named = member("fn");
    String namedActor = urls.actor(localActors.byUserId(named).orElseThrow().publicId());
    String alice = "https://" + host("mastodon") + "/users/alice";
    RemoteActorEntity account = account(alice, "alice");
    jdbc.update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        blocker,
        account.getId());
    serves(
        alice,
        "{\"id\":\"" + alice + "\",\"type\":\"Person\",\"outbox\":\"" + alice + "/outbox\"}");
    serves(alice + "/outbox", fixture("mastodon-outbox.json"));
    serves(
        alice + "/outbox?page=true",
        fixture("mastodon-outbox-page.json")
            .replace("KURL_MEMBER_ACTOR", namedActor)
            .replace("KURL_MEMBER_NAME", "fn" + tag));

    history.fillIn(account.getId(), reader);

    List<NoteView> listed =
        query.byRemoteActor(reader, account.getId(), new PageAsk(0, 20)).items();
    assertThat(listed)
        .extracting(NoteView::body)
        .containsExactly(
            "First frost tonight. Covered the last of the peppers. #garden",
            "Seed swap list is up, @fn" + tag + " you wanted the beans.",
            "Which editor do you write in?");
    assertThat(listed.get(2).poll().expired()).isTrue();
    assertThat(listed.get(1).media()).hasSize(1);
    assertThat(query.byRemoteActor(blocker, account.getId(), new PageAsk(0, 20)).items()).isEmpty();
    assertThat(notifications(named)).isZero();

    history.fillIn(account.getId(), reader);
    verify(http, times(3)).get(any(), any());

    jdbc.update(
        "UPDATE federation_remote_actor SET outbox_checked_at = UTC_TIMESTAMP(6) - INTERVAL 7 HOUR"
            + " WHERE id = ?",
        account.getId());
    history.fillIn(account.getId(), blocker);
    verify(http, times(3)).get(any(), any());

    JsonNode told =
        JSON.readTree(
            """
            {"id":"%s/statuses/9","type":"Note","attributedTo":"%s","content":"<p>hi</p>",
             "to":["https://www.w3.org/ns/activitystreams#Public"],
             "tag":[{"type":"Mention","href":"%s"}]}"""
                .formatted(alice, alice, namedActor));
    intake.keep(account, parser.parse(told, told), null);
    assertThat(notifications(named)).isEqualTo(1);

    history.fillIn(account.getId(), reader);
    verify(http, times(6)).get(any(), any());
    assertThat(query.byRemoteActor(reader, account.getId(), new PageAsk(0, 20)).items()).hasSize(4);
  }

  @Test
  void followingAnAccountFillsInItsHistoryInTheBackground() {
    Long reader = member("ff");
    String alice = "https://" + host("mastodon") + "/users/alice";
    RemoteActorEntity account = account(alice, "alice");
    serves(
        alice,
        "{\"id\":\"" + alice + "\",\"type\":\"Person\",\"outbox\":\"" + alice + "/outbox\"}");
    serves(alice + "/outbox", fixture("mastodon-outbox.json"));
    serves(alice + "/outbox?page=true", fixture("mastodon-outbox-page.json"));

    following.follow(reader, account.getId());
    awaitBackgroundWork();

    assertThat(query.byRemoteActor(reader, account.getId(), new PageAsk(0, 20)).items()).hasSize(3);
  }
}
