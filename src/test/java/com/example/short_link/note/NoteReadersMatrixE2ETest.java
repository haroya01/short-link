package com.example.short_link.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.federation.application.FederationActorService;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoteReadersMatrixE2ETest {

  enum Viewer {
    ANONYMOUS,
    STRANGER,
    FOLLOWER,
    MENTIONED,
    BLOCKED,
    MUTER,
    ROOMMATE,
    AUTHOR
  }

  enum Relation {
    ANYONE,
    AUTHOR,
    NAMED,
    FOLLOWER,
    MEMBER
  }

  enum Vis {
    PUBLIC(Relation.ANYONE),
    UNLISTED(Relation.ANYONE),
    PRIVATE(Relation.AUTHOR, Relation.NAMED, Relation.FOLLOWER),
    DIRECT(Relation.AUTHOR, Relation.NAMED),
    ROOM(Relation.AUTHOR, Relation.MEMBER);

    final Set<Relation> readers;

    Vis(Relation... readers) {
      this.readers = Set.of(readers);
    }

    boolean shareable() {
      return readers.contains(Relation.ANYONE);
    }

    String api() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  enum Writer {
    MEMBER,
    ELSEWHERE,
    OTHER
  }

  record Note(String kind, Vis vis, Writer writer, long id, String marker) {
    @Override
    public String toString() {
      return kind + "/" + vis + "/" + writer + "#" + id;
    }
  }

  record Seen(int status, String body) {}

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private BlockRepository blocks;
  @Autowired private MuteRepository mutes;
  @Autowired private NoteCommandService command;
  @Autowired private NoteRepository notes;
  @Autowired private NoteMediaRepository media;
  @Autowired private PostRepository posts;
  @Autowired private NoteViewReader noteViews;
  @Autowired private FederationActorService actors;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private String tag;
  private String mark;
  private String link;
  private final Map<Viewer, Long> ids = new EnumMap<>(Viewer.class);
  private final Map<Viewer, String> tokens = new EnumMap<>(Viewer.class);
  private final Map<Viewer, Long> lists = new EnumMap<>(Viewer.class);
  private long author;
  private long other;
  private String authorName;
  private String otherName;
  private long elsewhere;
  private long rootByOther;
  private long quotedByOther;
  private long embedRoot;
  private long room;
  private String inviteCode;
  private long postByOther;
  private long collection;
  private String seriesSlug;
  private String authorPublicId;
  private final List<Note> all = new ArrayList<>();
  private final Map<String, Note> byKey = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private int counter;

  @BeforeEach
  void everyKindOfReaderAndEveryVisibility() {
    tag = word();
    mark = word() + "x";
    link = "https://" + tag + ".example/read";
    for (Viewer viewer : Viewer.values()) {
      if (viewer == Viewer.ANONYMOUS) {
        continue;
      }
      UserEntity user = member(tag + viewer.name().substring(0, 2).toLowerCase(Locale.ROOT));
      ids.put(viewer, user.getId());
      tokens.put(viewer, jwt.createAccessToken(user.getId(), "USER"));
    }
    author = ids.get(Viewer.AUTHOR);
    authorName = users.findById(author).orElseThrow().getUsername();
    UserEntity otherUser = member(tag + "ot");
    other = otherUser.getId();
    otherName = otherUser.getUsername();
    follows.save(new FollowEntity(ids.get(Viewer.FOLLOWER), author));
    follows.save(new FollowEntity(ids.get(Viewer.MUTER), author));
    mutes.save(new UserMuteEntity(ids.get(Viewer.MUTER), author, false, null));
    blocks.save(new UserBlockEntity(author, ids.get(Viewer.BLOCKED)));
    em.flush();
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        author,
        tag + " room");
    room = jdbc.queryForObject("SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, author);
    jdbc.update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6)), (?, ?, NOW(6))",
        room,
        author,
        room,
        ids.get(Viewer.ROOMMATE));
    inviteCode = tag + "inv";
    jdbc.update(
        "INSERT INTO room_invite (room_id, creator_id, code, created_at) VALUES (?, ?, ?, NOW(6))",
        room,
        author,
        inviteCode);

    rootByOther = otherNote(Vis.PUBLIC, "root", null).id();
    quotedByOther = otherNote(Vis.PUBLIC, "quoted", null).id();
    embedRoot = otherNote(Vis.PUBLIC, "embedRoot", null).id();
    PostEntity post = new PostEntity(other, tag + "-post", tag + " post", "ko");
    post.publish();
    postByOther = posts.save(post).getId();
    em.flush();

    for (Vis vis : Vis.values()) {
      Note top = write("top", vis, null, null, null, false);
      media.saveAll(
          List.of(
              new NoteMediaEntity(
                  top.id(),
                  0,
                  "k/" + top.marker(),
                  "https://cdn.example/" + top.marker() + ".jpg",
                  "image/jpeg",
                  top.marker())));
      jdbc.update(
          "INSERT INTO note_link_preview (note_id, url, title, fetched_at) VALUES (?, ?, ?, NOW(6))",
          top.id(),
          link,
          top.marker());
      if (vis == Vis.ROOM) {
        write("roomReply", vis, top.id(), null, null, false);
        otherNote(Vis.PUBLIC, "embedRoom", top.id());
      } else {
        Note reply = write("reply", vis, rootByOther, null, null, false);
        Note hidden = write("hidden", vis, rootByOther, null, null, false);
        notes.findById(hidden.id()).orElseThrow().hideReply(Instant.now());
        otherNote(Vis.PUBLIC, "embed", reply.id());
      }
      write("quoting", vis, null, quotedByOther, null, false);
      write("postQuoting", vis, null, null, postByOther, false);
      write("poll", vis, null, null, null, true);
      Note trend = write("trend", vis, null, null, null, false);
      Note otherTrend = otherNote(vis, "otherTrend", null);
      notes.tag(trend.id(), List.of(tag + vis.api()));
      notes.tag(otherTrend.id(), List.of(tag + vis.api()));
      for (Note trending : List.of(trend, otherTrend)) {
        jdbc.update(
            "INSERT INTO note_link_preview (note_id, url, title, fetched_at)"
                + " VALUES (?, ?, ?, NOW(6))",
            trending.id(),
            link + "/" + vis.api(),
            trending.marker());
      }
    }
    for (Vis vis : List.of(Vis.PUBLIC, Vis.UNLISTED, Vis.PRIVATE, Vis.ROOM)) {
      command.setPin(author, note("top", vis).id(), true);
    }
    notes.findById(note("top", Vis.DIRECT).id()).orElseThrow().pin(Instant.now());
    em.flush();

    elsewhere = remoteAccount();
    for (Vis vis : List.of(Vis.PUBLIC, Vis.UNLISTED, Vis.PRIVATE, Vis.DIRECT)) {
      Note remote = remoteNote(vis);
      otherNote(Vis.PUBLIC, "embedElsewhere", remote.id());
    }
    em.flush();

    for (Note note : List.copyOf(all)) {
      if (note.kind().equals("top") || note.kind().equals("remote")) {
        jdbc.update(
            "INSERT INTO note_repost (note_id, user_id, created_at) VALUES (?, ?, NOW(6))",
            note.id(),
            other);
        for (Viewer viewer : signedIn()) {
          jdbc.update(
              "INSERT INTO note_bookmark (note_id, user_id, created_at) VALUES (?, ?, NOW(6))",
              note.id(),
              ids.get(viewer));
        }
      }
    }
    for (Viewer viewer : signedIn()) {
      if (viewer == Viewer.AUTHOR) {
        continue;
      }
      jdbc.update(
          "INSERT INTO note_list (user_id, title, created_at) VALUES (?, ?, NOW(6))",
          ids.get(viewer),
          "matrix");
      long list =
          jdbc.queryForObject(
              "SELECT MAX(id) FROM note_list WHERE user_id = ?", Long.class, ids.get(viewer));
      jdbc.update(
          "INSERT INTO note_list_member (list_id, member_id, created_at) VALUES (?, ?, NOW(6))",
          list,
          author);
      lists.put(viewer, list);
    }

    jdbc.update(
        "INSERT INTO collection (owner_id, title, visibility, created_at, updated_at)"
            + " VALUES (?, ?, 'PUBLIC', NOW(6), NOW(6))",
        author,
        tag + " shelf");
    collection =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM collection WHERE owner_id = ?", Long.class, author);
    seriesSlug = tag + "-series";
    jdbc.update(
        "INSERT INTO series (user_id, slug, title, created_at, updated_at)"
            + " VALUES (?, ?, ?, NOW(6), NOW(6))",
        author,
        seriesSlug,
        tag + " series");
    long series =
        jdbc.queryForObject("SELECT MAX(id) FROM series WHERE user_id = ?", Long.class, author);
    int position = 0;
    for (Vis vis : Vis.values()) {
      long top = note("top", vis).id();
      jdbc.update(
          "INSERT INTO collection_connection (collection_id, block_type, ref_id, position,"
              + " created_at) VALUES (?, 'NOTE', ?, ?, NOW(6))",
          collection,
          top,
          position);
      jdbc.update(
          "INSERT INTO series_item (series_id, item_type, ref_id, position, created_at)"
              + " VALUES (?, 'NOTE', ?, ?, NOW(6))",
          series,
          top,
          position);
      position++;
    }
    for (Viewer viewer : signedIn()) {
      jdbc.update(
          "INSERT INTO series_subscription (user_id, series_id, created_at) VALUES (?, ?, NOW(6))",
          ids.get(viewer),
          series);
    }
    authorPublicId = actors.byUsername(authorName).orElseThrow().publicId();
    em.flush();
    em.clear();
  }

  @Test
  void noReadPathShowsANoteToSomeoneWhoMayNotReadIt() throws Exception {
    for (Viewer viewer : Viewer.values()) {
      for (String path : listPaths()) {
        leaks(path, viewer, read(viewer, path));
      }
      for (Note note : all) {
        for (String path : notePaths(note)) {
          leaks(path, viewer, read(viewer, path));
        }
        leaks(
            "view " + note,
            viewer,
            new Seen(200, String.valueOf(noteViews.view(note.id(), ids.get(viewer)).orElse(""))));
      }
    }
    for (Note note : all) {
      leaks("/ap/notes/" + note.id(), Viewer.ANONYMOUS, activity("/ap/notes/" + note.id()));
    }

    assertThat(failures).as("%d read paths leaked", failures.size()).isEmpty();
  }

  @Test
  void eachListCarriesTheVisibilitiesItCarriesToday() throws Exception {
    List<Note> top = kind("top");
    List<Note> remote = kind("remote");
    List<Note> both = new ArrayList<>(top);
    both.addAll(remote);
    for (Viewer viewer : Viewer.values()) {
      Predicate<Note> heard = note -> viewer != Viewer.BLOCKED && viewer != Viewer.MUTER;
      Predicate<Note> listed = note -> note.vis() == Vis.PUBLIC;
      expect(
          "/api/v1/public/notes?size=50",
          viewer,
          top,
          listed.and(heard).or(note -> note.vis() == Vis.ROOM && member(viewer)));
      expect("/api/v1/public/notes?size=50", viewer, remote, note -> false);
      expect("/api/v1/public/notes?sort=trending&size=50", viewer, both, listed.and(heard));
      expect("/api/v1/public/notes/tags/" + tag + "?size=50", viewer, both, listed.and(heard));
      expect("/api/v1/public/notes/links?url=" + link + "&size=50", viewer, top, listed.and(heard));
      expect(
          "/api/v1/public/notes/" + quotedByOther + "/quotes?size=50",
          viewer,
          kind("quoting"),
          shareable().and(heard));
      expect(
          "/api/v1/public/posts/" + postByOther + "/quotes?size=50",
          viewer,
          kind("postQuoting"),
          shareable().and(heard));
      expect(
          "/api/v1/public/notes/" + rootByOther,
          viewer,
          kind("reply"),
          readableBy(viewer).and(heard));
      List<Note> embedded = new ArrayList<>(kind("reply"));
      embedded.addAll(remote);
      expect(
          "/api/v1/public/notes/" + embedRoot,
          viewer,
          embedded,
          readableBy(viewer)
              .and(note -> note.writer() == Writer.MEMBER || viewer != Viewer.BLOCKED));
      expect(
          "/api/v1/public/notes/" + rootByOther + "/hidden-replies",
          viewer,
          kind("hidden"),
          readableBy(viewer).and(heard));
      for (String tab : List.of("notes", "media")) {
        expect(
            "/api/v1/public/profiles/" + authorName + "/" + tab + "?size=50",
            viewer,
            top,
            onProfile(viewer));
      }
      expect(
          "/api/v1/public/profiles/" + authorName + "/replies?size=50",
          viewer,
          kind("reply"),
          onProfile(viewer));
      expect(
          "/api/v1/public/profiles/" + otherName + "/reposts?size=50",
          viewer,
          both,
          readableBy(viewer).and(heard));
      expect("/api/v1/collections/" + collection, viewer, top, note -> note.vis().shareable());
      expect(
          "/api/v1/public/profiles/" + authorName + "/series/" + seriesSlug,
          viewer,
          top,
          note -> note.vis().shareable());
      trends("/api/v1/public/notes/trending-tags", viewer, vis -> "\"" + tag + vis.api() + "\"");
      trends("/api/v1/public/notes/trending-links", viewer, vis -> link + "/" + vis.api() + "\"");
      if (viewer == Viewer.ANONYMOUS) {
        continue;
      }
      expect(
          "/api/v1/notes/federated?size=50",
          viewer,
          both,
          note -> note.writer() == Writer.ELSEWHERE && listed.and(heard).test(note));
      expect(
          "/api/v1/federation/accounts/" + elsewhere + "/notes?size=50",
          viewer,
          remote,
          readableBy(viewer).and(note -> viewer != Viewer.BLOCKED));
      Predicate<Note> subscribed =
          note -> note.kind().equals("top") && note.vis().shareable() && heard.test(note);
      expect("/api/v1/notes/following?size=50", viewer, both, following(viewer).or(subscribed));
      expect(
          "/api/v1/public/home?tab=following&limit=40",
          viewer,
          both,
          following(viewer).or(subscribed));
      List<Note> withReplies = new ArrayList<>(both);
      withReplies.addAll(kind("reply"));
      expect(
          "/api/v1/notes/direct?size=50",
          viewer,
          withReplies,
          readableBy(viewer)
              .and(note -> note.vis() == Vis.DIRECT && viewer != Viewer.BLOCKED)
              .and(note -> viewer == Viewer.MENTIONED || note.writer() == Writer.MEMBER));
      expect(
          "/api/v1/notes/bookmarks?size=50",
          viewer,
          both,
          readableBy(viewer)
              .and(note -> note.writer() == Writer.MEMBER || viewer != Viewer.BLOCKED));
      expect("/api/v1/feed/following?size=50", viewer, top, shareable().and(heard));
      if (lists.containsKey(viewer)) {
        expect(
            "/api/v1/notes/lists/" + lists.get(viewer) + "/notes?size=50",
            viewer,
            top,
            readableBy(viewer).and(heard).and(note -> note.vis() != Vis.ROOM));
      }
      expect(
          "/api/v1/rooms/" + room + "/notes?size=50",
          viewer,
          top,
          note -> note.vis() == Vis.ROOM && member(viewer));
      expect(
          "/api/v1/rooms/my-notes?size=50",
          viewer,
          top,
          note -> note.vis() == Vis.ROOM && viewer == Viewer.AUTHOR);
    }
    Seen featured = activity("/ap/actors/" + authorPublicId + "/featured");
    Set<String> pinned =
        kind("top").stream()
            .filter(note -> featured.body().contains("/ap/notes/" + note.id() + "\""))
            .map(note -> note.vis().name())
            .collect(Collectors.toCollection(TreeSet::new));
    if (!pinned.equals(Set.of("PUBLIC", "UNLISTED"))) {
      failures.add("featured lists " + pinned);
    }
    long shared =
        all.stream()
            .filter(
                note ->
                    note.writer() == Writer.MEMBER
                        && note.vis() != Vis.DIRECT
                        && note.vis() != Vis.ROOM)
            .count();
    String outbox = activity("/ap/actors/" + authorPublicId + "/outbox").body();
    if (!outbox.contains("\"totalItems\":" + shared + ",")) {
      failures.add("outbox counts " + outbox + " instead of " + shared);
    }

    assertThat(failures).as("%d lists differ from today's rules", failures.size()).isEmpty();
  }

  @Test
  void aNoteSomeoneMayNotReadDoesNotExistForThem() throws Exception {
    for (Viewer viewer : Viewer.values()) {
      for (Note note : all) {
        if (note.writer() == Writer.OTHER) {
          continue;
        }
        boolean opens = readableBy(viewer).test(note) && viewer != Viewer.BLOCKED;
        for (String path :
            List.of(
                "/api/v1/public/notes/" + note.id(),
                "/api/v1/public/notes/" + note.id() + "/history",
                "/api/v1/public/notes/" + note.id() + "/hidden-replies")) {
          Seen seen = read(viewer, path);
          if ((seen.status() == 200) != opens) {
            failures.add(viewer + " " + path + " " + note + " -> " + seen.status());
          }
          if (opens && path.equals("/api/v1/public/notes/" + note.id())) {
            if (!seen.body().contains(note.marker())) {
              failures.add(viewer + " " + path + " missing its own body");
            }
          }
        }
        boolean served = noteViews.view(note.id(), ids.get(viewer)).isPresent();
        if (served != opens) {
          failures.add(viewer + " view " + note + " -> " + served);
        }
      }
    }
    for (Note note : all) {
      if (note.writer() == Writer.OTHER) {
        continue;
      }
      boolean object = note.writer() == Writer.MEMBER && note.vis().shareable();
      int status = activity("/ap/notes/" + note.id()).status();
      if ((status == 200) != object) {
        failures.add("/ap/notes " + note + " -> " + status);
      }
    }

    assertThat(failures).as("%d detail reads differ", failures.size()).isEmpty();
  }

  @Test
  void actingOnANoteSomeoneMayNotReadIsNotFound() throws Exception {
    List<Note> targets = new ArrayList<>(kind("top"));
    targets.addAll(kind("remote"));
    targets.addAll(kind("poll"));
    for (Viewer viewer : signedIn()) {
      for (Note note : targets) {
        boolean readable = readableBy(viewer).test(note);
        List<MockHttpServletRequestBuilder> acts =
            new ArrayList<>(
                List.of(
                    put("/api/v1/notes/" + note.id() + "/like"),
                    put("/api/v1/notes/" + note.id() + "/bookmark"),
                    put("/api/v1/notes/" + note.id() + "/conversation-mute"),
                    put("/api/v1/notes/" + note.id() + "/repost"),
                    post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"answer\",\"inReplyToId\":" + note.id() + "}"),
                    post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"quote\",\"quotedNoteId\":" + note.id() + "}")));
        if (note.kind().equals("poll")) {
          acts.add(
              post("/api/v1/notes/" + note.id() + "/poll/votes")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"choices\":[0]}"));
        }
        for (MockHttpServletRequestBuilder act : acts) {
          MvcResult result =
              mvc.perform(act.header("Authorization", "Bearer " + tokens.get(viewer))).andReturn();
          int status = result.getResponse().getStatus();
          String what = result.getRequest().getMethod() + " " + result.getRequest().getRequestURI();
          if (!readable && status != 404) {
            failures.add(viewer + " " + what + " on unreadable " + note + " -> " + status);
          }
          if (readable && status == 404) {
            failures.add(viewer + " " + what + " on readable " + note + " -> 404");
          }
        }
      }
    }

    assertThat(failures).as("%d actions differ", failures.size()).isEmpty();
  }

  private List<String> listPaths() {
    List<String> paths =
        new ArrayList<>(
            List.of(
                "/api/v1/public/notes?size=50",
                "/api/v1/public/notes?sort=trending&size=50",
                "/api/v1/public/notes/trending-tags",
                "/api/v1/public/notes/trending-links",
                "/api/v1/public/notes/tags/" + tag + "?size=50",
                "/api/v1/public/notes/search?q=" + tag + "&size=50",
                "/api/v1/public/notes/links?url=" + link + "&size=50",
                "/api/v1/public/notes/" + quotedByOther + "/quotes?size=50",
                "/api/v1/public/posts/" + postByOther + "/quotes?size=50",
                "/api/v1/public/notes/" + quotedByOther + "/posts",
                "/api/v1/public/profiles/" + authorName + "/notes?size=50",
                "/api/v1/public/profiles/" + authorName + "/replies?size=50",
                "/api/v1/public/profiles/" + authorName + "/media?size=50",
                "/api/v1/public/profiles/" + otherName + "/notes?size=50",
                "/api/v1/public/profiles/" + otherName + "/reposts?size=50",
                "/api/v1/collections/" + collection,
                "/api/v1/public/profiles/" + authorName + "/collections",
                "/api/v1/public/feed/connections",
                "/api/v1/public/graph/blocks/note/" + note("top", Vis.PUBLIC).id() + "/related",
                "/api/v1/public/profiles/" + authorName + "/series",
                "/api/v1/public/profiles/" + authorName + "/series/" + seriesSlug,
                "/api/v1/public/series",
                "/api/v1/notes/federated?size=50",
                "/api/v1/federation/accounts/" + elsewhere + "/notes?size=50",
                "/api/v1/notes/following?size=50",
                "/api/v1/public/home?tab=following&limit=40",
                "/api/v1/notes/direct?size=50",
                "/api/v1/notes/bookmarks?size=50",
                "/api/v1/notes/scheduled",
                "/api/v1/feed/following?size=50",
                "/api/v1/feed/connections",
                "/api/v1/series",
                "/api/v1/notifications",
                "/api/v1/rooms",
                "/api/v1/rooms/" + room,
                "/api/v1/rooms/" + room + "/notes?size=50",
                "/api/v1/rooms/my-notes?size=50",
                "/api/v1/rooms/suggestions",
                "/api/v1/public/room-invites/" + inviteCode));
    lists.values().forEach(list -> paths.add("/api/v1/notes/lists/" + list + "/notes?size=50"));
    return paths;
  }

  private static List<String> notePaths(Note note) {
    return List.of(
        "/api/v1/public/notes/" + note.id(),
        "/api/v1/public/notes/" + note.id() + "/history",
        "/api/v1/public/notes/" + note.id() + "/hidden-replies",
        "/api/v1/public/notes/" + note.id() + "/quotes?size=50");
  }

  private void leaks(String path, Viewer viewer, Seen seen) {
    for (Note note : all) {
      if (!readableBy(viewer).test(note) && seen.body().contains(note.marker())) {
        failures.add("LEAK " + viewer + " " + path + " shows " + note);
      }
    }
  }

  private void expect(String path, Viewer viewer, List<Note> probes, Predicate<Note> carried)
      throws Exception {
    Seen seen = read(viewer, path);
    Set<String> want = new TreeSet<>();
    Set<String> got = new TreeSet<>();
    for (Note note : probes) {
      if (carried.test(note)) {
        want.add(note.toString());
      }
      if (seen.body().contains(note.marker())) {
        got.add(note.toString());
      }
    }
    if (!want.equals(got)) {
      failures.add(viewer + " " + path + " [" + seen.status() + "] want " + want + " got " + got);
    }
  }

  private void trends(String path, Viewer viewer, java.util.function.Function<Vis, String> name)
      throws Exception {
    Seen seen = read(viewer, path);
    Set<Vis> trending = new TreeSet<>();
    for (Vis vis : Vis.values()) {
      if (seen.body().contains(name.apply(vis))) {
        trending.add(vis);
      }
    }
    if (!trending.equals(Set.of(Vis.PUBLIC))) {
      failures.add(viewer + " " + path + " trends " + trending);
    }
  }

  private Predicate<Note> readableBy(Viewer viewer) {
    return note -> note.vis().readers.stream().anyMatch(relations(viewer, note)::contains);
  }

  private static Set<Relation> relations(Viewer viewer, Note note) {
    Set<Relation> held = new java.util.HashSet<>(Set.of(Relation.ANYONE));
    if (viewer == Viewer.AUTHOR && note.writer() == Writer.MEMBER) {
      held.add(Relation.AUTHOR);
    }
    if (viewer == Viewer.MENTIONED && note.writer() != Writer.OTHER) {
      held.add(Relation.NAMED);
    }
    if ((viewer == Viewer.FOLLOWER || viewer == Viewer.MUTER) && note.writer() != Writer.OTHER) {
      held.add(Relation.FOLLOWER);
    }
    if ((viewer == Viewer.ROOMMATE || viewer == Viewer.AUTHOR) && note.writer() == Writer.MEMBER) {
      held.add(Relation.MEMBER);
    }
    return held;
  }

  private static boolean member(Viewer viewer) {
    return viewer == Viewer.ROOMMATE || viewer == Viewer.AUTHOR;
  }

  private static Predicate<Note> shareable() {
    return note -> note.vis().shareable();
  }

  private Predicate<Note> onProfile(Viewer viewer) {
    return note ->
        viewer != Viewer.BLOCKED
            && note.vis() != Vis.ROOM
            && (viewer == Viewer.AUTHOR
                || note.vis().shareable()
                || (note.vis() == Vis.PRIVATE
                    && (viewer == Viewer.FOLLOWER
                        || viewer == Viewer.MUTER
                        || viewer == Viewer.MENTIONED)));
  }

  private static Predicate<Note> following(Viewer viewer) {
    return note ->
        switch (viewer) {
          case AUTHOR -> note.writer() == Writer.MEMBER;
          case FOLLOWER -> note.vis() != Vis.DIRECT && note.vis() != Vis.ROOM;
          case MENTIONED -> note.vis() == Vis.DIRECT;
          case ROOMMATE -> note.vis() == Vis.ROOM;
          default -> false;
        };
  }

  private Seen read(Viewer viewer, String path) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    if (viewer != Viewer.ANONYMOUS) {
      request.header("Authorization", "Bearer " + tokens.get(viewer));
    }
    MvcResult result = mvc.perform(request).andReturn();
    return new Seen(result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private Seen activity(String path) throws Exception {
    MvcResult result =
        mvc.perform(get(path).header("Accept", "application/activity+json")).andReturn();
    return new Seen(result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private List<Viewer> signedIn() {
    return List.of(Viewer.values()).subList(1, Viewer.values().length);
  }

  private List<Note> kind(String kind) {
    return all.stream().filter(note -> note.kind().equals(kind)).toList();
  }

  private Note note(String kind, Vis vis) {
    return byKey.get(kind + "/" + vis);
  }

  private Note write(
      String kind, Vis vis, Long inReplyTo, Long quotedNote, Long quotedPost, boolean poll) {
    String marker = marker();
    String body =
        marker
            + " #"
            + tag
            + " @"
            + users.findById(ids.get(Viewer.MENTIONED)).orElseThrow().getUsername()
            + (kind.equals("top") ? " " + link : "");
    long id =
        command
            .create(
                author,
                new NoteDraft(
                    body,
                    null,
                    quotedPost,
                    inReplyTo,
                    quotedNote,
                    null,
                    false,
                    vis.api(),
                    poll ? new NoteDraft.Poll(List.of("yes", "no"), 3600L, false) : null,
                    null,
                    null,
                    vis == Vis.ROOM && inReplyTo == null ? room : null))
            .id();
    em.flush();
    return remember(new Note(kind, vis, Writer.MEMBER, id, marker));
  }

  private Note otherNote(Vis vis, String kind, Long quotedNote) {
    String marker = marker();
    boolean embeds = quotedNote != null;
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, quoted_note_id, in_reply_to_id,"
            + " conversation_id, reply) VALUES (?, ?, NOW(6), ?, ?, ?, ?, ?)",
        other,
        embeds ? marker : marker + " #" + tag,
        vis.name(),
        quotedNote,
        embeds ? embedRoot : null,
        embeds ? embedRoot : null,
        embeds);
    long id = jdbc.queryForObject("SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, other);
    return remember(new Note(kind, vis, Writer.OTHER, id, marker));
  }

  private long remoteAccount() {
    String domain = tag + ".elsewhere.example";
    String uri = "https://" + domain + "/users/" + tag;
    jdbc.update(
        "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox, username,"
            + " domain, indexable, fetched_at, created_at, updated_at, outbox_checked_at)"
            + " VALUES (?, ?, 'pem', ?, ?, ?, TRUE, NOW(6), NOW(6), NOW(6), NOW(6))",
        uri,
        uri + "#main-key",
        uri + "/inbox",
        tag,
        domain);
    long id =
        jdbc.queryForObject(
            "SELECT id FROM federation_remote_actor WHERE actor_uri = ?", Long.class, uri);
    for (Viewer viewer : List.of(Viewer.FOLLOWER, Viewer.MUTER)) {
      jdbc.update(
          "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
              + " accepted_at, created_at, updated_at) VALUES (?, ?, ?, NOW(6), NOW(6), NOW(6))",
          ids.get(viewer),
          id,
          "https://kurl.example/follows/" + UUID.randomUUID());
    }
    jdbc.update(
        "INSERT INTO federation_remote_mute (user_id, remote_actor_id, hide_notifications,"
            + " created_at) VALUES (?, ?, FALSE, NOW(6))",
        ids.get(Viewer.MUTER),
        id);
    jdbc.update(
        "INSERT INTO federation_remote_block (user_id, remote_actor_id, created_at)"
            + " VALUES (?, ?, NOW(6))",
        ids.get(Viewer.BLOCKED),
        id);
    return id;
  }

  private Note remoteNote(Vis vis) {
    String marker = marker();
    String uri = "https://" + tag + ".elsewhere.example/notes/" + UUID.randomUUID();
    jdbc.update(
        "INSERT INTO note (remote_actor_id, uri, body, created_at, visibility)"
            + " VALUES (?, ?, ?, NOW(6), ?)",
        elsewhere,
        uri,
        marker + " #" + tag,
        vis.name());
    long id = jdbc.queryForObject("SELECT id FROM note WHERE uri = ?", Long.class, uri);
    notes.tag(id, List.of(tag));
    if (!vis.shareable()) {
      notes.addRecipients(id, List.of(ids.get(Viewer.MENTIONED)));
    }
    return remember(new Note("remote", vis, Writer.ELSEWHERE, id, marker));
  }

  private Note remember(Note note) {
    all.add(note);
    byKey.putIfAbsent(note.kind() + "/" + note.vis(), note);
    return note;
  }

  private String marker() {
    counter++;
    return mark + "n" + String.format("%03d", counter) + "q";
  }

  private UserEntity member(String handle) {
    UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
    user.claimUsername(handle);
    return users.save(user);
  }

  private static String word() {
    String consonants = "bdfgkmprstvz";
    StringBuilder word = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      word.append(consonants.charAt(ThreadLocalRandom.current().nextInt(consonants.length())));
      word.append(i % 2 == 0 ? 'a' : 'i');
    }
    return word.toString();
  }
}
