package com.example.short_link.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class RoomQuotesE2ETest {

  enum Who {
    ANONYMOUS,
    STRANGER,
    NEIGHBOR,
    BOTH,
    ROOMMATE,
    AUTHOR
  }

  private static final Set<Who> MEMBERS = EnumSet.of(Who.BOTH, Who.ROOMMATE, Who.AUTHOR);

  record Answer(int status, JsonNode body, String raw) {}

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  @Value("${short-link.blog-base-url}")
  private String blogBaseUrl;

  private final ObjectMapper json = new ObjectMapper();
  private final Map<Who, Long> ids = new EnumMap<>(Who.class);
  private final Map<Who, String> tokens = new EnumMap<>(Who.class);
  private final Map<Who, String> names = new EnumMap<>(Who.class);
  private final List<String> failures = new ArrayList<>();
  private String tag;
  private long room;
  private long nextDoor;
  private long publicNote;
  private long followersNote;
  private long roomNote;
  private long nextDoorNote;
  private long openPost;
  private long roomPost;
  private long nextDoorPost;

  @BeforeEach
  void aRoomARoomNextDoorAndSomethingOfEachKindToQuote() throws Exception {
    tag = word();
    for (Who who : Who.values()) {
      if (who == Who.ANONYMOUS) {
        continue;
      }
      String handle = tag + who.name().substring(0, 3).toLowerCase(Locale.ROOT);
      UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
      user.claimUsername(handle);
      ids.put(who, users.save(user).getId());
      names.put(who, handle);
      tokens.put(who, jwt.createAccessToken(ids.get(who), "USER"));
    }
    follows.save(
        new FollowEntity(
            FollowEdge.follower(ids.get(Who.ROOMMATE)).following(ids.get(Who.AUTHOR))));
    em.flush();
    room = room(Who.AUTHOR, Who.ROOMMATE, Who.BOTH);
    nextDoor = room(Who.NEIGHBOR, Who.BOTH);

    publicNote = note(Who.AUTHOR, mark("open"), null, null);
    followersNote = note(Who.AUTHOR, mark("followers"), null, "private");
    roomNote = note(Who.AUTHOR, mark("roomnote"), room, null);
    nextDoorNote = note(Who.NEIGHBOR, mark("nextdoornote"), nextDoor, null);
    openPost = publish(Who.AUTHOR, "open", mark("openpost"), null);
    roomPost = publish(Who.AUTHOR, "room", mark("roompost"), room);
    nextDoorPost = publish(Who.NEIGHBOR, "nextdoor", mark("nextdoorpost"), nextDoor);
    em.flush();
    em.clear();
  }

  @Test
  void whatEveryoneSeesIsQuotedAnywhereAndWhatARoomHoldsOnlyInsideThatRoom() throws Exception {
    quoting("a public note outside rooms", Who.ROOMMATE, Map.of("quotedNoteId", publicNote), 201);
    JsonNode publicInRoom =
        quoting(
                "a public note in a room",
                Who.ROOMMATE,
                Map.of("quotedNoteId", publicNote, "roomId", room),
                201)
            .body();
    quoting("an open post outside rooms", Who.ROOMMATE, Map.of("quotedPostId", openPost), 201);
    JsonNode openInRoom =
        quoting(
                "an open post in a room",
                Who.ROOMMATE,
                Map.of("quotedPostId", openPost, "roomId", room),
                201)
            .body();
    JsonNode roomNoteInRoom =
        quoting(
                "a room note in its room",
                Who.ROOMMATE,
                Map.of("quotedNoteId", roomNote, "roomId", room),
                201)
            .body();
    JsonNode roomPostInRoom =
        quoting(
                "a room post in its room",
                Who.ROOMMATE,
                Map.of("quotedPostId", roomPost, "roomId", room),
                201)
            .body();

    assertThat(publicInRoom.path("quotedNote").path("id").asLong()).isEqualTo(publicNote);
    assertThat(publicInRoom.path("room").path("id").asLong()).isEqualTo(room);
    assertThat(openInRoom.path("quotedPost").path("audience").asText()).isEqualTo("PUBLIC");
    assertThat(roomNoteInRoom.path("quotedNote").path("id").asLong()).isEqualTo(roomNote);
    assertThat(roomNoteInRoom.path("visibility").asText()).isEqualTo("room");
    assertThat(roomPostInRoom.path("quotedPost").path("id").asLong()).isEqualTo(roomPost);
    assertThat(roomPostInRoom.path("quotedPost").path("audience").asText()).isEqualTo("ROOM");
    assertThat(roomPostInRoom.path("quotedPost").has("locked")).isFalse();

    refused(
        "a room note from outside rooms",
        Who.ROOMMATE,
        Map.of("quotedNoteId", roomNote),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a room note from a followers-only note",
        Who.ROOMMATE,
        Map.of("quotedNoteId", roomNote, "visibility", "private"),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a room note from another room the writer is in",
        Who.BOTH,
        Map.of("quotedNoteId", roomNote, "roomId", nextDoor),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a followers-only note from a room",
        Who.ROOMMATE,
        Map.of("quotedNoteId", followersNote, "roomId", room),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a followers-only note from outside rooms",
        Who.ROOMMATE,
        Map.of("quotedNoteId", followersNote),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a room note the writer may not read",
        Who.NEIGHBOR,
        Map.of("quotedNoteId", roomNote, "roomId", nextDoor),
        404,
        "NOTE_NOT_FOUND");
    Answer missing =
        refused(
            "a post that does not exist",
            Who.ROOMMATE,
            Map.of("quotedPostId", Long.MAX_VALUE),
            400,
            "NOTE_QUOTE_NOT_FOUND");
    for (Answer hidden :
        List.of(
            refused(
                "a room post from outside rooms",
                Who.ROOMMATE,
                Map.of("quotedPostId", roomPost),
                400,
                "NOTE_QUOTE_NOT_FOUND"),
            refused(
                "a room post from another room the writer is in",
                Who.BOTH,
                Map.of("quotedPostId", roomPost, "roomId", nextDoor),
                400,
                "NOTE_QUOTE_NOT_FOUND"),
            refused(
                "a room post by someone outside its room",
                Who.STRANGER,
                Map.of("quotedPostId", roomPost),
                400,
                "NOTE_QUOTE_NOT_FOUND"))) {
      assertThat(shape(hidden)).isEqualTo(shape(missing));
    }
    Answer notMine =
        refused(
            "a room post into its room by someone outside it",
            Who.STRANGER,
            Map.of("quotedPostId", roomPost, "roomId", room),
            404,
            "NOTE_ROOM_NOT_FOUND");
    Answer notMineAndMissing =
        refused(
            "a missing post into a room by someone outside it",
            Who.STRANGER,
            Map.of("quotedPostId", Long.MAX_VALUE, "roomId", room),
            404,
            "NOTE_ROOM_NOT_FOUND");
    assertThat(shape(notMine)).isEqualTo(shape(notMineAndMissing));

    assertThat(failures).as("%d rows of the table differ", failures.size()).isEmpty();
  }

  @Test
  void aReplyAThreadPartAndAScheduledNoteGetTheSameAnswer() throws Exception {
    long roomTop = note(Who.ROOMMATE, "room top " + tag, room, null);
    quoting(
        "a room note from a reply in its room",
        Who.ROOMMATE,
        Map.of("quotedNoteId", roomNote, "inReplyToId", roomTop),
        201);
    refused(
        "a room note from a reply outside rooms",
        Who.ROOMMATE,
        Map.of("quotedNoteId", roomNote, "inReplyToId", publicNote),
        400,
        "NOTE_NOT_SHAREABLE");
    refused(
        "a room post from a reply outside rooms",
        Who.ROOMMATE,
        Map.of("quotedPostId", roomPost, "inReplyToId", publicNote),
        400,
        "NOTE_QUOTE_NOT_FOUND");

    expect(
        "a thread in the room whose second part quotes the room's note",
        thread(Map.of("roomId", room), Map.of("quotedNoteId", roomNote)),
        201,
        null);
    expect(
        "a thread in the room whose second part quotes the room's post",
        thread(Map.of("roomId", room), Map.of("quotedPostId", roomPost)),
        201,
        null);
    expect(
        "a thread outside rooms whose second part quotes a room note",
        thread(Map.of(), Map.of("quotedNoteId", roomNote)),
        400,
        "NOTE_NOT_SHAREABLE");
    expect(
        "a thread outside rooms whose second part quotes a room post",
        thread(Map.of(), Map.of("quotedPostId", roomPost)),
        400,
        "NOTE_QUOTE_NOT_FOUND");

    expect(
        "a scheduled room note quoting the room's note",
        scheduled(Map.of("quotedNoteId", roomNote, "roomId", room)),
        201,
        null);
    expect(
        "a scheduled room note quoting the room's post",
        scheduled(Map.of("quotedPostId", roomPost, "roomId", room)),
        201,
        null);
    expect(
        "a scheduled note outside rooms quoting a room note",
        scheduled(Map.of("quotedNoteId", roomNote)),
        400,
        "NOTE_NOT_SHAREABLE");
    expect(
        "a scheduled note outside rooms quoting a room post",
        scheduled(Map.of("quotedPostId", roomPost)),
        400,
        "NOTE_QUOTE_NOT_FOUND");

    assertThat(failures).as("%d draft paths differ", failures.size()).isEmpty();
  }

  @Test
  void aRoomNoteIsStillNotReposted() throws Exception {
    Answer repost = act(Who.ROOMMATE, put("/api/v1/notes/" + roomNote + "/repost"));

    assertThat(repost.status()).isEqualTo(400);
    assertThat(repost.body().path("code").asText()).isEqualTo("NOTE_NOT_SHAREABLE");
    assertThat(count("SELECT COUNT(*) FROM note_repost WHERE note_id = ?", roomNote)).isZero();
  }

  @Test
  void aQuotedPostsCardShowsOutsideRoomsOrInsideThePostsOwnRoom() throws Exception {
    long inRoomOfRoomPost = quote(Who.ROOMMATE, Map.of("quotedPostId", roomPost, "roomId", room));
    long inRoomOfOpenPost = quote(Who.ROOMMATE, Map.of("quotedPostId", openPost, "roomId", room));
    long outsideOfOpenPost = quote(Who.STRANGER, Map.of("quotedPostId", openPost));
    long nextDoorOfRoomPost =
        forged(Who.BOTH, "next door of the room post", nextDoor, null, roomPost);
    em.flush();
    em.clear();

    for (Who member : MEMBERS) {
      JsonNode card = thread(member, inRoomOfRoomPost).path("quotedPost");
      assertThat(card.path("id").asLong()).as("%s", member).isEqualTo(roomPost);
      assertThat(card.path("audience").asText()).isEqualTo("ROOM");
      assertThat(card.has("locked")).isFalse();
      assertThat(read(member, "/api/v1/rooms/" + room + "/notes?size=50").raw())
          .contains(mark("roompost"));
      assertThat(read(member, "/api/v1/rooms/feed?size=50").raw()).contains(mark("roompost"));
    }
    assertThat(
            thread(Who.BOTH, nextDoorOfRoomPost).path("quotedPost").isMissingNode()
                || thread(Who.BOTH, nextDoorOfRoomPost).path("quotedPost").isNull())
        .as("a room post never shows on a note of another room")
        .isTrue();
    assertThat(read(Who.BOTH, "/api/v1/rooms/" + nextDoor + "/notes?size=50").raw())
        .doesNotContain(mark("roompost"));

    Answer left =
        act(
            Who.AUTHOR,
            patch("/api/v1/posts/" + roomPost)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":\"PUBLIC\"}"));
    Answer entered =
        act(
            Who.AUTHOR,
            patch("/api/v1/posts/" + openPost)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":\"ROOM\",\"roomId\":" + room + "}"));
    assertThat(left.status()).as(left.raw()).isEqualTo(200);
    assertThat(entered.status()).as(entered.raw()).isEqualTo(200);
    em.flush();
    em.clear();

    assertThat(thread(Who.ROOMMATE, inRoomOfRoomPost).path("quotedPost").path("audience").asText())
        .as("a post that left its room is quoted like any open post")
        .isEqualTo("PUBLIC");
    assertThat(thread(Who.ROOMMATE, inRoomOfOpenPost).path("quotedPost").path("audience").asText())
        .as("a post that moved into the room keeps its card on that room's notes")
        .isEqualTo("ROOM");
    for (Who viewer : Who.values()) {
      JsonNode outside = thread(viewer, outsideOfOpenPost);
      assertThat(outside.path("id").asLong()).isEqualTo(outsideOfOpenPost);
      assertThat(outside.path("quotedPost").isMissingNode() || outside.path("quotedPost").isNull())
          .as("%s sees no card of a post that moved into a room on a public note", viewer)
          .isTrue();
    }
  }

  @Test
  void aQuoteIsCountedAndListedOnlyOnTheQuotedOnesSideOfTheWall() throws Exception {
    long openQuote = quote(Who.STRANGER, Map.of("quotedNoteId", publicNote));
    quote(Who.ROOMMATE, Map.of("quotedNoteId", publicNote, "roomId", room));
    quote(Who.ROOMMATE, Map.of("quotedNoteId", publicNote, "visibility", "private"));
    long openEmbed = publish(Who.STRANGER, "embeds-open", "embeds open", null, publicNote);
    publish(Who.ROOMMATE, "embeds-open-in-room", mark("roomembedsopen"), room, publicNote);

    long roomQuote = quote(Who.ROOMMATE, Map.of("quotedNoteId", roomNote, "roomId", room));
    long ownRoomQuote = quote(Who.AUTHOR, Map.of("quotedNoteId", roomNote, "roomId", room));
    forged(Who.STRANGER, "forged from outside", null, roomNote, null);
    forged(Who.BOTH, "forged from next door", nextDoor, roomNote, null);
    long roomEmbed = publish(Who.ROOMMATE, "embeds-room", mark("roomembed"), room, roomNote);
    long roomEmbedAgain = publish(Who.AUTHOR, "embeds-room-again", "again", room, roomNote);
    publish(Who.BOTH, "embeds-room-openly", "pasted outside", null, roomNote);
    publish(Who.BOTH, "embeds-room-next-door", "pasted next door", nextDoor, roomNote);

    long openPostQuote = quote(Who.STRANGER, Map.of("quotedPostId", openPost));
    quote(Who.ROOMMATE, Map.of("quotedPostId", openPost, "roomId", room));
    quote(Who.ROOMMATE, Map.of("quotedPostId", openPost, "visibility", "private"));
    long roomPostQuote = quote(Who.ROOMMATE, Map.of("quotedPostId", roomPost, "roomId", room));
    long ownRoomPostQuote = quote(Who.AUTHOR, Map.of("quotedPostId", roomPost, "roomId", room));
    forged(Who.STRANGER, "forged post quote from outside", null, null, roomPost);
    forged(Who.BOTH, "forged post quote from next door", nextDoor, null, roomPost);
    em.flush();
    em.clear();

    String noQuotes =
        read(Who.ANONYMOUS, "/api/v1/public/notes/" + Long.MAX_VALUE + "/quotes").raw();
    String noPosts = read(Who.ANONYMOUS, "/api/v1/public/notes/" + Long.MAX_VALUE + "/posts").raw();
    String noPostQuotes =
        read(Who.ANONYMOUS, "/api/v1/public/posts/" + Long.MAX_VALUE + "/quotes").raw();
    for (Who viewer : Who.values()) {
      boolean member = MEMBERS.contains(viewer);
      assertThat(thread(viewer, publicNote).path("quoteCount").asLong())
          .as("%s: a public note counts its public quotes and the open posts carrying it", viewer)
          .isEqualTo(2);
      assertThat(listed(viewer, "/api/v1/public/notes/" + publicNote + "/quotes?size=50"))
          .as("%s: quotes of a public note", viewer)
          .containsExactly(openQuote);
      assertThat(listed(viewer, "/api/v1/public/notes/" + publicNote + "/posts"))
          .as("%s: posts carrying a public note", viewer)
          .containsExactly(openEmbed);

      Answer quotes = read(viewer, "/api/v1/public/notes/" + roomNote + "/quotes?size=50");
      Answer posts = read(viewer, "/api/v1/public/notes/" + roomNote + "/posts");
      Answer postQuotes = read(viewer, "/api/v1/public/posts/" + roomPost + "/quotes?size=50");
      if (member) {
        assertThat(thread(viewer, roomNote).path("quoteCount").asLong())
            .as("%s: a room note counts the quotes and posts of its own room", viewer)
            .isEqualTo(4);
        assertThat(ids(quotes))
            .as("%s: quotes of a room note", viewer)
            .containsExactly(ownRoomQuote, roomQuote);
        assertThat(ids(posts))
            .as("%s: posts carrying a room note", viewer)
            .containsExactly(roomEmbedAgain, roomEmbed);
        assertThat(ids(postQuotes))
            .as("%s: notes quoting a room post", viewer)
            .containsExactly(ownRoomPostQuote, roomPostQuote);
        assertThat(postQuotes.body().path("total").asLong()).isEqualTo(2);
      } else {
        assertThat(read(viewer, "/api/v1/public/notes/" + roomNote).status()).isEqualTo(404);
        assertThat(quotes.raw()).as("%s: quotes of a room note", viewer).isEqualTo(noQuotes);
        assertThat(posts.raw()).as("%s: posts carrying a room note", viewer).isEqualTo(noPosts);
        assertThat(postQuotes.raw())
            .as("%s: notes quoting a room post", viewer)
            .isEqualTo(noPostQuotes);
        assertThat(read(viewer, "/api/v1/public/notes/" + roomNote + "/quotes?size=1").raw())
            .as("%s: no sign of a second page of a room note's quotes", viewer)
            .isEqualTo(noQuotes);
        assertThat(read(viewer, "/api/v1/public/posts/" + roomPost + "/quotes?size=1").raw())
            .as("%s: no sign of a second page of a room post's quotes", viewer)
            .isEqualTo(noPostQuotes);
      }

      Answer openPostQuotes = read(viewer, "/api/v1/public/posts/" + openPost + "/quotes?size=50");
      assertThat(ids(openPostQuotes))
          .as("%s: notes quoting an open post", viewer)
          .containsExactly(openPostQuote);
      assertThat(openPostQuotes.body().path("total").asLong()).isEqualTo(1);
    }
  }

  @Test
  void whoLeftARoomKeepingTheirNotesSeesNothingOfItThroughTheirOwnQuotes() throws Exception {
    long ofNote = quote(Who.ROOMMATE, Map.of("quotedNoteId", roomNote, "roomId", room));
    long ofPost = quote(Who.ROOMMATE, Map.of("quotedPostId", roomPost, "roomId", room));
    Answer left =
        act(
            Who.ROOMMATE,
            post("/api/v1/rooms/" + room + "/leave")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deleteMyNotes\":false}"));
    assertThat(left.status()).as(left.raw()).isEqualTo(204);
    em.flush();
    em.clear();

    Answer ownQuoteOfPost = read(Who.ROOMMATE, "/api/v1/public/notes/" + ofPost);
    Answer ownQuoteOfNote = read(Who.ROOMMATE, "/api/v1/public/notes/" + ofNote);
    assertThat(ownQuoteOfPost.status()).as(ownQuoteOfPost.raw()).isEqualTo(200);
    assertThat(ownQuoteOfPost.body().path("note").path("id").asLong()).isEqualTo(ofPost);
    assertThat(ownQuoteOfPost.raw()).doesNotContain(mark("roompost"));
    assertThat(ownQuoteOfNote.status()).as(ownQuoteOfNote.raw()).isEqualTo(200);
    assertThat(ownQuoteOfNote.raw()).doesNotContain(mark("roomnote"));
    assertThat(read(Who.ROOMMATE, "/api/v1/rooms/my-notes?size=50").raw())
        .contains("quoting " + tag)
        .doesNotContain(mark("roompost"))
        .doesNotContain(mark("roomnote"));
    assertThat(read(Who.ROOMMATE, "/api/v1/public/notes/" + roomNote + "/quotes").raw())
        .isEqualTo(read(Who.ANONYMOUS, "/api/v1/public/notes/" + Long.MAX_VALUE + "/quotes").raw());
    assertThat(read(Who.ROOMMATE, "/api/v1/public/posts/" + roomPost + "/quotes").raw())
        .isEqualTo(read(Who.ANONYMOUS, "/api/v1/public/posts/" + Long.MAX_VALUE + "/quotes").raw());

    assertThat(thread(Who.AUTHOR, ofPost).path("quotedPost").path("id").asLong())
        .isEqualTo(roomPost);
    assertThat(listed(Who.AUTHOR, "/api/v1/public/notes/" + roomNote + "/quotes"))
        .containsExactly(ofNote);
    assertThat(listed(Who.AUTHOR, "/api/v1/public/posts/" + roomPost + "/quotes"))
        .containsExactly(ofPost);
  }

  private Answer quoting(String what, Who who, Map<String, Object> quote, int status)
      throws Exception {
    return expect(what, write(who, quote), status, null);
  }

  private Answer refused(String what, Who who, Map<String, Object> quote, int status, String code)
      throws Exception {
    return expect(what, write(who, quote), status, code);
  }

  private Answer expect(String what, Answer answer, int status, String code) {
    String got = answer.body().path("code").asText("");
    if (answer.status() != status || (code != null && !code.equals(got))) {
      failures.add(what + " -> " + answer.status() + " " + got);
    }
    return answer;
  }

  private static String shape(Answer answer) {
    return answer.status()
        + " "
        + answer.body().path("code").asText()
        + " "
        + answer.body().path("detail").asText().replaceAll("\\d+", "#");
  }

  private Answer write(Who who, Map<String, Object> quote) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>(quote);
    body.put("body", "quoting " + tag);
    return act(
        who,
        post("/api/v1/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private long quote(Who who, Map<String, Object> quote) throws Exception {
    Answer answer = write(who, quote);
    assertThat(answer.status()).as(answer.raw()).isEqualTo(201);
    return answer.body().path("id").asLong();
  }

  private Answer thread(Map<String, Object> first, Map<String, Object> second) throws Exception {
    Map<String, Object> one = new LinkedHashMap<>(first);
    one.put("body", "first " + tag);
    Map<String, Object> two = new LinkedHashMap<>(second);
    two.put("body", "second " + tag);
    return act(
        Who.ROOMMATE,
        post("/api/v1/notes/threads")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("notes", List.of(one, two)))));
  }

  private Answer scheduled(Map<String, Object> quote) throws Exception {
    Map<String, Object> note = new LinkedHashMap<>(quote);
    note.put("body", "later " + tag);
    return act(
        Who.ROOMMATE,
        post("/api/v1/notes/scheduled")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of(
                        "note",
                        note,
                        "scheduledAt",
                        Instant.now().plus(1, ChronoUnit.HOURS).toString()))));
  }

  private JsonNode thread(Who viewer, long noteId) throws Exception {
    Answer answer = read(viewer, "/api/v1/public/notes/" + noteId);
    assertThat(answer.status())
        .as("%s reads note %d: %s", viewer, noteId, answer.raw())
        .isEqualTo(200);
    return answer.body().path("note");
  }

  private List<Long> listed(Who viewer, String path) throws Exception {
    return ids(read(viewer, path));
  }

  private static List<Long> ids(Answer answer) {
    assertThat(answer.status()).as(answer.raw()).isEqualTo(200);
    List<Long> ids = new ArrayList<>();
    answer.body().path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  private long note(Who writer, String body, Long inRoom, String visibility) throws Exception {
    Map<String, Object> draft = new LinkedHashMap<>(Map.of("body", body));
    if (inRoom != null) {
      draft.put("roomId", inRoom);
    }
    if (visibility != null) {
      draft.put("visibility", visibility);
    }
    Answer answer =
        act(
            writer,
            post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(draft)));
    assertThat(answer.status()).as(answer.raw()).isEqualTo(201);
    return answer.body().path("id").asLong();
  }

  private long forged(Who writer, String body, Long inRoom, Long quotedNote, Long quotedPost) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at, visibility, room_id, quoted_note_id,"
            + " quoted_post_id) VALUES (?, ?, NOW(6), ?, ?, ?, ?)",
        ids.get(writer),
        body + " " + tag,
        inRoom == null ? "PUBLIC" : "ROOM",
        inRoom,
        quotedNote,
        quotedPost);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, ids.get(writer));
  }

  private long publish(Who writer, String slug, String title, Long inRoom, long... embedded)
      throws Exception {
    Map<String, Object> create =
        new LinkedHashMap<>(Map.of("slug", tag + "-" + slug, "title", title));
    if (inRoom != null) {
      create.put("audience", "ROOM");
      create.put("roomId", inRoom);
    }
    Answer created =
        act(
            writer,
            post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(create)));
    assertThat(created.status()).as(created.raw()).isEqualTo(201);
    long id = created.body().path("id").asLong();
    List<Map<String, String>> blocks = new ArrayList<>();
    blocks.add(Map.of("type", "PARAGRAPH", "content", "a post " + tag));
    for (long noteId : embedded) {
      blocks.add(
          Map.of(
              "type",
              "EMBED",
              "content",
              blogBaseUrl + "/@" + names.get(Who.AUTHOR) + "/notes/" + noteId));
    }
    Answer saved =
        act(
            writer,
            put("/api/v1/posts/" + id + "/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("blocks", blocks))));
    assertThat(saved.status()).as(saved.raw()).isEqualTo(200);
    Answer published = act(writer, post("/api/v1/posts/" + id + "/publish"));
    assertThat(published.status()).as(published.raw()).isEqualTo(200);
    em.flush();
    return id;
  }

  private Answer act(Who who, MockHttpServletRequestBuilder request) throws Exception {
    return answer(mvc.perform(request.header("Authorization", "Bearer " + tokens.get(who))));
  }

  private Answer read(Who viewer, String path) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    if (viewer != Who.ANONYMOUS) {
      request.header("Authorization", "Bearer " + tokens.get(viewer));
    }
    return answer(mvc.perform(request));
  }

  private Answer answer(ResultActions actions) throws Exception {
    MvcResult result = actions.andReturn();
    String raw = result.getResponse().getContentAsString();
    return new Answer(
        result.getResponse().getStatus(),
        raw.isBlank() ? json.nullNode() : json.readTree(raw),
        raw);
  }

  private long room(Who owner, Who... others) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        ids.get(owner),
        tag + " " + owner);
    long id =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, ids.get(owner));
    jdbc.update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
        id,
        ids.get(owner));
    for (Who other : others) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          id,
          ids.get(other));
    }
    return id;
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private String mark(String key) {
    return tag + "x" + key;
  }

  private static String word() {
    String consonants = "bdfgkmprstvz";
    StringBuilder word = new StringBuilder();
    for (int i = 0; i < 5; i++) {
      word.append(consonants.charAt(ThreadLocalRandom.current().nextInt(consonants.length())));
      word.append(i % 2 == 0 ? 'a' : 'i');
    }
    return word.toString();
  }
}
