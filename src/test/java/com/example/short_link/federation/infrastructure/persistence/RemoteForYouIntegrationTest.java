package com.example.short_link.federation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.common.seen.SeenViewer;
import com.example.short_link.federation.domain.foryou.ReachTarget;
import com.example.short_link.federation.domain.repository.FederationRelayedNoteRepository;
import com.example.short_link.federation.domain.repository.ForYouSignalReader;
import com.example.short_link.federation.domain.repository.ForYouSignalReader.Kind;
import com.example.short_link.federation.domain.repository.RemoteDwellRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counted;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Counts;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Due;
import com.example.short_link.federation.domain.repository.RemoteNoteReachRepository.Postponed;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RemoteForYouIntegrationTest {

  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

  @Autowired private NoteViewReader notes;
  @Autowired private FederationRelayedNoteRepository relayed;
  @Autowired private RemoteNoteReachRepository reach;
  @Autowired private RemoteDwellRepository dwells;
  @Autowired private ForYouSignalReader signals;
  @Autowired private UserRepository users;
  @Autowired private EntityManager em;
  @Autowired private JdbcTemplate jdbc;

  private String tag;
  private long sequence;
  private Long viewer;
  private Long alice;
  private Long bob;

  @BeforeEach
  void peopleHereAndElsewhere() {
    tag = "y" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    viewer = user(tag);
    alice = actor("alice");
    bob = actor("bob");
  }

  private Long user(String name) {
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    return users.save(user).getId();
  }

  private String domain(String username) {
    return username + "-" + tag + ".example";
  }

  private Long actor(String username) {
    String uri = "https://" + domain(username) + "/users/" + username;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
            + " username, domain, fetched_at, created_at, updated_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        username + tag,
        domain(username));
    return jdbc.queryForObject(
        "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);
  }

  private String uriOf(Long actorId, long n) {
    return "https://" + actorId + "-" + tag + ".example/users/u/statuses/" + n;
  }

  private Long note(Long actorId, Instant at, String language, String warning, boolean sensitive) {
    String uri = uriOf(actorId, ++sequence);
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility, reply, language,"
            + " content_warning, marked_sensitive) VALUES (?, ?, 'hi', ?, 'PUBLIC', FALSE, ?, ?, ?)",
        actorId,
        uri,
        LocalDateTime.ofInstant(at, ZoneOffset.UTC),
        language,
        warning,
        sensitive);
    return jdbc.queryForObject("SELECT id FROM note WHERE uri = ?", Long.class, uri);
  }

  private Long pulled(Long actorId, Instant at) {
    Long note = note(actorId, at, "ko", null, false);
    relayed.markPulled(note, "pulled-" + tag + ".example", at);
    return note;
  }

  private String uri(Long noteId) {
    return jdbc.queryForObject("SELECT uri FROM note WHERE id = ?", String.class, noteId);
  }

  private List<Long> pool(Long viewerId) {
    return notes.remotePool(viewerId, NOW.minus(Duration.ofHours(72)), NOW, 1_000, false).stream()
        .filter(row -> row.domain().endsWith(tag + ".example"))
        .map(NoteViewReader.Pooled::id)
        .toList();
  }

  @Test
  void thePoolIsTheLastThreeDaysOfKeptNotesWithoutWarningsOrAccountsTheViewerFollows() {
    Long newest = pulled(alice, NOW.minus(Duration.ofHours(1)));
    Long tagged = pulled(bob, NOW.minus(Duration.ofHours(2)));
    jdbc.update(
        "INSERT INTO note_tag (note_id, tag) VALUES (?, 'java'), (?, '일상')", tagged, tagged);
    pulled(alice, NOW.minus(Duration.ofHours(73)));
    Long warned = note(alice, NOW.minus(Duration.ofHours(1)), "ko", "spoiler", false);
    relayed.mark(warned, null, NOW.minus(Duration.ofHours(1)));
    Long sensitive = note(alice, NOW.minus(Duration.ofHours(1)), "ko", null, true);
    relayed.mark(sensitive, null, NOW.minus(Duration.ofHours(1)));
    Long later = note(alice, NOW.minus(Duration.ofHours(1)), "ko", null, false);
    relayed.mark(later, null, NOW.plusSeconds(60));

    assertThat(pool(viewer)).containsExactly(tagged, newest);
    NoteViewReader.Pooled row =
        notes.remotePool(viewer, NOW.minus(Duration.ofHours(72)), NOW, 1_000, false).stream()
            .filter(pooled -> pooled.id().equals(tagged))
            .findFirst()
            .orElseThrow();
    assertThat(row.remoteActorId()).isEqualTo(bob);
    assertThat(row.domain()).isEqualTo(domain("bob"));
    assertThat(row.language()).isEqualTo("ko");
    assertThat(row.tags()).containsExactlyInAnyOrder("java", "일상");
    assertThat(row.uri()).isEqualTo(uri(tagged));

    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        viewer,
        alice,
        "https://kurl.me/follow/" + tag);
    jdbc.update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        viewer,
        bob);
    assertThat(pool(viewer)).isEmpty();
    assertThat(pool(null)).containsExactly(tagged, newest);
  }

  @Test
  void theHomesPoolKeepsAccountsTheViewerFollowsAndWhenEachNoteCameIn() {
    Instant came = NOW.minus(Duration.ofMinutes(30));
    Long followed = note(alice, NOW.minus(Duration.ofHours(2)), "ko", null, false);
    relayed.markPulled(followed, "pulled-" + tag + ".example", came);
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        viewer,
        alice,
        "https://kurl.me/follow/home-" + tag);

    List<NoteViewReader.Pooled> home =
        notes.remotePool(viewer, NOW.minus(Duration.ofHours(72)), NOW, 1_000, true).stream()
            .filter(row -> row.id().equals(followed))
            .toList();

    assertThat(pool(viewer)).doesNotContain(followed);
    assertThat(home).hasSize(1);
    assertThat(home.getFirst().receivedAt()).isEqualTo(came);
  }

  @Test
  void aNoteIsHydratedInTheOrderAskedAndOneThatIsGoneIsLeftOut() {
    Long first = pulled(alice, NOW.minus(Duration.ofHours(1)));
    Long second = pulled(bob, NOW.minus(Duration.ofHours(1)));

    List<NoteViewReader.Listed> listed = notes.listed(viewer, List.of(second, -1L, first));

    assertThat(listed).extracting(NoteViewReader.Listed::id).containsExactly(second, first);
    assertThat(listed.getFirst().uri()).isEqualTo(uri(second));
    assertThat(notes.listed(viewer, List.of())).isEmpty();
  }

  @Test
  void aTrackedNoteKeepsItsFirstRowTrendingOnlyRaisesItsCountsAndAnAskSetsThem() {
    Long note = pulled(alice, NOW.minus(Duration.ofHours(1)));
    Long other = pulled(bob, NOW.minus(Duration.ofHours(1)));
    ReachTarget target = new ReachTarget("q.example", "101");

    reach.track(note, target, new Counts(1, 2, 3), NOW, NOW.plus(Duration.ofHours(1)));
    reach.track(note, null, new Counts(9, 9, 9), NOW, NOW);
    reach.track(other, null, Counts.NONE, NOW, NOW);
    assertThat(reach.of(List.of(note, other, -1L)))
        .containsOnlyKeys(note, other)
        .containsEntry(note, new RemoteNoteReachRepository.Reach(note, new Counts(1, 2, 3), false));

    reach.trended(Map.of(note, new Counts(0, 5, 3), other, new Counts(4, 0, 0)), NOW);
    assertThat(reach.of(List.of(note)).get(note).counts()).isEqualTo(new Counts(1, 5, 3));
    assertThat(
            jdbc.queryForObject(
                "SELECT trending_at IS NOT NULL FROM remote_note_reach WHERE note_id = ?",
                Boolean.class,
                other))
        .isTrue();

    assertThat(reach.due(NOW.minus(Duration.ofHours(6)), NOW.plus(Duration.ofHours(2)), 10))
        .filteredOn(due -> due.noteId().equals(note) || due.noteId().equals(other))
        .containsExactly(new Due(note, "q.example", "101", 0, 0));
    assertThat(reach.due(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)), 10))
        .noneMatch(due -> due.noteId().equals(note));

    reach.counted(List.of(new Counted(note, new Counts(0, 1, 0), NOW, null, 3)));
    assertThat(reach.of(List.of(note)).get(note).counts()).isEqualTo(new Counts(0, 1, 0));
    reach.postponed(
        List.of(
            new Postponed(note, NOW.plus(Duration.ofHours(2)), 2, null),
            new Postponed(other, null, 0, NOW),
            new Postponed(-1L, null, 0, NOW)));
    assertThat(
            jdbc.queryForObject(
                "SELECT CONCAT(checks, '/', failures, '/', next_check_at IS NOT NULL)"
                    + " FROM remote_note_reach WHERE note_id = ?",
                String.class,
                note))
        .isEqualTo("3/2/1");
    assertThat(reach.of(List.of(other)).get(other).gone()).isTrue();
    reach.postponed(List.of(new Postponed(other, null, 1, null)));
    assertThat(reach.of(List.of(other)).get(other).gone()).isTrue();
    reach.counted(List.of());
    reach.postponed(List.of());
    reach.trended(Map.of(), NOW);
    assertThat(reach.of(List.of())).isEmpty();
  }

  @Test
  void aNoteIsFoundByItsUriAndANoteDeletedSinceIsSkipped() {
    Long note = pulled(alice, NOW.minus(Duration.ofHours(1)));

    assertThat(reach.idsByUri(List.of(uri(note), "https://nowhere.example/x")))
        .containsExactly(Map.entry(uri(note), note));
    assertThat(reach.idsByUri(List.of())).isEmpty();
    reach.trended(Map.of(-5L, new Counts(1, 1, 1)), NOW);
    reach.counted(List.of(new Counted(-5L, new Counts(1, 1, 1), NOW, null, 1)));
    assertThat(reach.of(List.of(-5L))).isEmpty();
  }

  @Test
  void aLongerDwellReplacesAShorterOneAndOldOnesAreSweptInBatches() {
    Long note = pulled(alice, NOW.minus(Duration.ofHours(1)));
    SeenViewer member = SeenViewer.member(viewer);
    Instant earlier = NOW.minus(Duration.ofDays(40));

    dwells.record(List.of(new RemoteDwellRepository.Dwell(member, note, 9_000, earlier)));
    dwells.record(
        List.of(
            new RemoteDwellRepository.Dwell(member, note, 3_000, NOW),
            new RemoteDwellRepository.Dwell(member, -1L, 3_000, NOW)));
    assertThat(
            jdbc.queryForObject(
                "SELECT dwell_ms FROM remote_dwell WHERE note_id = ?", Integer.class, note))
        .isEqualTo(9_000);
    assertThat(dwells.deleteBefore(NOW.minus(Duration.ofDays(30)), 10)).isEqualTo(1);

    dwells.record(List.of(new RemoteDwellRepository.Dwell(member, note, 9_000, earlier)));
    dwells.record(List.of(new RemoteDwellRepository.Dwell(member, note, 12_000, NOW)));
    assertThat(dwells.deleteBefore(NOW.minus(Duration.ofDays(30)), 10)).isZero();
    dwells.record(List.of());
  }

  @Test
  void aMembersSignalsCarryTheNotesAuthorLanguageAndTagsAndADeviceHasOnlyItsDwells() {
    Long liked = note(alice, NOW.minus(Duration.ofHours(1)), "ja", null, false);
    jdbc.update(
        "INSERT INTO note_tag (note_id, tag) VALUES (?, 'java'), (?, 'spring')", liked, liked);
    jdbc.update(
        "INSERT INTO note_like (note_id, user_id, created_at) VALUES (?, ?, UTC_TIMESTAMP(6))",
        liked,
        viewer);
    Long saved = note(bob, NOW.minus(Duration.ofHours(1)), "ko", null, false);
    jdbc.update(
        "INSERT INTO note_bookmark (note_id, user_id, created_at)"
            + " VALUES (?, ?, UTC_TIMESTAMP(6))",
        saved,
        viewer);
    Long author = user(tag + "w");
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, reply, language)"
            + " VALUES (?, 'mine', UTC_TIMESTAMP(6), 'PUBLIC', FALSE, 'en')",
        author);
    Long local =
        jdbc.queryForObject(
            "SELECT id FROM note WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, author);
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, reply, in_reply_to_id)"
            + " VALUES (?, 'yes', UTC_TIMESTAMP(6), 'PUBLIC', TRUE, ?)",
        viewer,
        local);
    Long carol = actor("carol");
    Long followedNote = note(carol, NOW.minus(Duration.ofHours(1)), "ko", null, false);
    jdbc.update(
        "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
            + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
        viewer,
        carol,
        "https://kurl.me/follow/" + tag);
    Long dwelt = note(bob, NOW.minus(Duration.ofHours(1)), "ko", null, false);
    Long glanced = note(bob, NOW.minus(Duration.ofHours(1)), "ko", null, false);
    SeenViewer member = SeenViewer.member(viewer);
    SeenViewer device = SeenViewer.of(null, UUID.randomUUID().toString());
    dwells.record(
        List.of(
            new RemoteDwellRepository.Dwell(member, dwelt, 9_000, NOW),
            new RemoteDwellRepository.Dwell(member, glanced, 2_000, NOW),
            new RemoteDwellRepository.Dwell(device, dwelt, 20_000, NOW)));

    List<ForYouSignalReader.Signal> read =
        signals.signals(member, NOW.minus(Duration.ofDays(30)), 40, 8_000);

    assertThat(read)
        .containsExactlyInAnyOrder(
            new ForYouSignalReader.Signal(Kind.LIKED, "r" + alice, "ja", List.of("java", "spring")),
            new ForYouSignalReader.Signal(Kind.BOOKMARKED, "r" + bob, "ko", List.of()),
            new ForYouSignalReader.Signal(Kind.ANSWERED, "u" + author, "en", List.of()),
            new ForYouSignalReader.Signal(Kind.FOLLOWED, "r" + carol, "ko", List.of()),
            new ForYouSignalReader.Signal(Kind.DWELT, "r" + bob, "ko", List.of()));
    assertThat(signals.signals(device, NOW.minus(Duration.ofDays(30)), 40, 8_000))
        .containsExactly(new ForYouSignalReader.Signal(Kind.DWELT, "r" + bob, "ko", List.of()));
    assertThat(signals.signals(member, NOW.plus(Duration.ofDays(1)), 40, 8_000)).isEmpty();
    assertThat(signals.followed(viewer)).containsExactly(carol);
    assertThat(signals.locale(viewer)).contains("ko");
    assertThat(signals.locale(-1L)).isEmpty();
    assertThat(followedNote).isNotNull();
  }

  @Test
  void aTrendedNoteCountsTowardItsOwnDayAndNotThePullsNorTheirNewest() {
    Instant start = NOW.minus(Duration.ofMinutes(1));
    long pulledBefore = relayed.pulledSince(start);
    long trendedBefore = relayed.trendedSince(start);
    Long pulledNote = pulled(alice, NOW);
    Long trendedNote = note(alice, NOW.plusSeconds(1), "ko", null, false);
    relayed.markTrended(trendedNote, "pulled-" + tag + ".example", NOW);

    assertThat(relayed.pulledSince(start)).isEqualTo(pulledBefore + 1);
    assertThat(relayed.trendedSince(start)).isEqualTo(trendedBefore + 1);
    assertThat(relayed.newestPulled("pulled-" + tag + ".example"))
        .contains(
            jdbc.queryForObject(
                    "SELECT created_at FROM note WHERE id = ?", LocalDateTime.class, pulledNote)
                .toInstant(ZoneOffset.UTC));
  }
}
