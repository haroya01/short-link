package com.example.short_link.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class RoomQuoteNoticesE2ETest {

  enum Who {
    STRANGER,
    LEFT,
    ROOMMATE,
    AUTHOR
  }

  record Notice(String type, long about, long by) {}

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("webhookExecutor")
  private ThreadPoolTaskExecutor notices;

  @Value("${short-link.blog-base-url}")
  private String blogBaseUrl;

  private final ObjectMapper json = new ObjectMapper();
  private final Map<Who, Long> ids = new EnumMap<>(Who.class);
  private final Map<Who, String> tokens = new EnumMap<>(Who.class);
  private final Map<Who, String> names = new EnumMap<>(Who.class);
  private String tag;
  private Long room;
  private long roomNote;
  private long leftNote;
  private long leftQuote;
  private long openPost;
  private long roomPost;
  private long strangerPost;
  private long strangerNote;

  @BeforeEach
  void aRoomOneMemberLeftKeepingTheirNotes() throws Exception {
    tag = "rq" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    for (Who who : Who.values()) {
      String handle = tag + who.name().substring(0, 2).toLowerCase(Locale.ROOT);
      UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
      user.claimUsername(handle);
      ids.put(who, users.save(user).getId());
      names.put(who, handle);
      tokens.put(who, jwt.createAccessToken(ids.get(who), "USER"));
    }
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        ids.get(Who.AUTHOR),
        tag);
    room = jdbc.queryForObject("SELECT id FROM room WHERE name = ?", Long.class, tag);
    for (Who member : List.of(Who.AUTHOR, Who.ROOMMATE, Who.LEFT)) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          room,
          ids.get(member));
    }
    roomNote = note(Who.AUTHOR, Map.of("body", "room note " + tag, "roomId", room));
    leftNote = note(Who.LEFT, Map.of("body", "left behind " + tag, "roomId", room));
    leftQuote =
        note(
            Who.LEFT,
            Map.of("body", "left quote " + tag, "roomId", room, "quotedNoteId", roomNote));
    openPost = publish(Who.AUTHOR, "open", null);
    roomPost = publish(Who.AUTHOR, "room", room);
    strangerPost = publish(Who.STRANGER, "strangers", null);
    strangerNote = note(Who.STRANGER, Map.of("body", "outside " + tag));
    MvcResult left =
        mvc.perform(
                post("/api/v1/rooms/" + room + "/leave")
                    .header("Authorization", "Bearer " + tokens.get(Who.LEFT))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"deleteMyNotes\":false}"))
            .andReturn();
    assertThat(left.getResponse().getStatus()).isEqualTo(204);
    drain();
    forgetNotices();
  }

  @AfterEach
  void deleteCommittedRows() {
    forgetNotices();
    for (Long user : ids.values()) {
      for (String table : List.of("post_block", "post_revision")) {
        jdbc.update(
            "DELETE t FROM " + table + " t JOIN posts p ON p.id = t.post_id WHERE p.user_id = ?",
            user);
      }
      jdbc.update("DELETE FROM posts WHERE user_id = ?", user);
      jdbc.update(
          "DELETE FROM note_edit WHERE note_id IN (SELECT id FROM note WHERE user_id = ?)", user);
      jdbc.update("DELETE FROM note WHERE user_id = ?", user);
    }
    if (room != null) {
      jdbc.update("DELETE FROM room WHERE id = ?", room);
    }
    for (Long user : ids.values()) {
      jdbc.update("DELETE FROM users WHERE id = ?", user);
    }
  }

  @Test
  void aQuoteOfARoomNoteTellsItsAuthorOnlyWhileTheyAreInTheRoom() throws Exception {
    long quote =
        note(Who.ROOMMATE, Map.of("body", "look " + tag, "roomId", room, "quotedNoteId", roomNote));
    note(Who.AUTHOR, Map.of("body", "my own " + tag, "roomId", room, "quotedNoteId", roomNote));
    note(
        Who.ROOMMATE,
        Map.of("body", "still here " + tag, "roomId", room, "quotedNoteId", leftNote));
    drain();

    assertThat(told(Who.AUTHOR))
        .containsExactly(new Notice("NOTE_QUOTE", quote, ids.get(Who.ROOMMATE)));
    assertThat(told(Who.LEFT)).isEmpty();
    assertThat(listed(Who.AUTHOR, "NOTE_QUOTE").path("sourceNoteId").asLong()).isEqualTo(quote);
    assertThat(listed(Who.AUTHOR, "NOTE_QUOTE").path("noteId").asLong()).isEqualTo(roomNote);
  }

  @Test
  void aQuoteOfAPostMadeInARoomTellsThePostsAuthorOnlyWhenTheyAreInThatRoom() throws Exception {
    long ofRoomPost =
        note(
            Who.ROOMMATE,
            Map.of("body", "room post " + tag, "roomId", room, "quotedPostId", roomPost));
    long ofOpenPost =
        note(
            Who.ROOMMATE,
            Map.of("body", "open post " + tag, "roomId", room, "quotedPostId", openPost));
    long named =
        note(
            Who.ROOMMATE,
            Map.of(
                "body",
                "@" + names.get(Who.AUTHOR) + " again " + tag,
                "roomId",
                room,
                "quotedPostId",
                roomPost));
    note(
        Who.AUTHOR, Map.of("body", "my own post " + tag, "roomId", room, "quotedPostId", roomPost));
    note(
        Who.ROOMMATE,
        Map.of(
            "body",
            "@" + names.get(Who.STRANGER) + " in here " + tag,
            "roomId",
            room,
            "quotedPostId",
            strangerPost));
    long openly = note(Who.ROOMMATE, Map.of("body", "openly " + tag, "quotedPostId", strangerPost));
    drain();

    long roommate = ids.get(Who.ROOMMATE);
    assertThat(told(Who.AUTHOR))
        .containsExactlyInAnyOrder(
            new Notice("POST_QUOTE", ofRoomPost, roommate),
            new Notice("POST_QUOTE", ofOpenPost, roommate),
            new Notice("POST_QUOTE", named, roommate));
    assertThat(told(Who.STRANGER)).containsExactly(new Notice("POST_QUOTE", openly, roommate));
  }

  @Test
  void aNoteCarriedByAPostTellsItsAuthorOnlyOnThePostsSideOfTheWall() throws Exception {
    long roommateNote =
        note(Who.ROOMMATE, Map.of("body", "mine in the room " + tag, "roomId", room));
    drain();
    forgetNotices();

    long inRoom =
        publish(Who.AUTHOR, "carries", room, roommateNote, leftNote, strangerNote, roomNote);
    drain();

    long author = ids.get(Who.AUTHOR);
    assertThat(told(Who.ROOMMATE)).containsExactly(new Notice("NOTE_EMBED", inRoom, author));
    assertThat(told(Who.LEFT)).isEmpty();
    assertThat(told(Who.STRANGER)).isEmpty();
    assertThat(told(Who.AUTHOR)).isEmpty();
    forgetNotices();

    long outside = publish(Who.AUTHOR, "carries-outside", null, roommateNote, strangerNote);
    drain();

    assertThat(told(Who.STRANGER)).containsExactly(new Notice("NOTE_EMBED", outside, author));
    assertThat(told(Who.ROOMMATE)).isEmpty();
  }

  @Test
  void anEditOfAQuotedRoomNoteTellsOnlyQuotersStillInTheRoom() throws Exception {
    note(Who.ROOMMATE, Map.of("body", "quoting " + tag, "roomId", room, "quotedNoteId", roomNote));
    drain();
    forgetNotices();

    MvcResult edited =
        mvc.perform(
                patch("/api/v1/notes/" + roomNote)
                    .header("Authorization", "Bearer " + tokens.get(Who.AUTHOR))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"room note said again " + tag + "\"}"))
            .andReturn();
    assertThat(edited.getResponse().getStatus()).isEqualTo(200);
    drain();

    assertThat(
            count(
                "SELECT COUNT(*) FROM note WHERE id = ? AND quoted_note_id = ?",
                leftQuote,
                roomNote))
        .as("the member who left still has a quote of the note in the room")
        .isOne();
    assertThat(told(Who.ROOMMATE))
        .containsExactly(new Notice("NOTE_EDIT", roomNote, ids.get(Who.AUTHOR)));
    assertThat(told(Who.LEFT)).isEmpty();
  }

  private List<Notice> told(Who who) {
    return jdbc.query(
        "SELECT type, actor_user_id, COALESCE(payload->>'$.sourceNoteId', payload->>'$.noteId',"
            + " payload->>'$.postId') AS about FROM notification WHERE recipient_user_id = ?"
            + " ORDER BY id",
        (row, i) -> new Notice(row.getString("type"), row.getLong("about"), row.getLong(2)),
        ids.get(who));
  }

  private JsonNode listed(Who who, String type) throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/notifications?limit=50")
                    .header("Authorization", "Bearer " + tokens.get(who)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<JsonNode> found = new ArrayList<>();
    json.readTree(body)
        .path("items")
        .forEach(
            item -> {
              if (item.path("type").asText().equals(type)) {
                found.add(item);
              }
            });
    assertThat(found).as(body).hasSize(1);
    return found.getFirst();
  }

  private long note(Who writer, Map<String, Object> draft) throws Exception {
    MvcResult result = act(writer, post("/api/v1/notes"), draft);
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(201);
    return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private long publish(Who writer, String slug, Long inRoom, long... embedded) throws Exception {
    Map<String, Object> create =
        new LinkedHashMap<>(Map.of("slug", tag + "-" + slug, "title", slug + " " + tag));
    if (inRoom != null) {
      create.put("audience", "ROOM");
      create.put("roomId", inRoom);
    }
    MvcResult created = act(writer, post("/api/v1/posts"), create);
    assertThat(created.getResponse().getStatus())
        .as(created.getResponse().getContentAsString())
        .isEqualTo(201);
    long id = json.readTree(created.getResponse().getContentAsString()).path("id").asLong();
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
    assertThat(
            act(writer, put("/api/v1/posts/" + id + "/blocks"), Map.of("blocks", blocks))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(
            act(writer, post("/api/v1/posts/" + id + "/publish"), null).getResponse().getStatus())
        .isEqualTo(200);
    return id;
  }

  private MvcResult act(Who who, MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    request.header("Authorization", "Bearer " + tokens.get(who));
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
    return mvc.perform(request).andReturn();
  }

  private void forgetNotices() {
    for (Long user : ids.values()) {
      jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", user);
      jdbc.update("DELETE FROM notification_permission WHERE recipient_user_id = ?", user);
    }
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private void drain() throws InterruptedException {
    long until = System.currentTimeMillis() + 15_000;
    int quiet = 0;
    while (System.currentTimeMillis() < until && quiet < 5) {
      boolean idle =
          notices.getActiveCount() == 0 && notices.getThreadPoolExecutor().getQueue().isEmpty();
      quiet = idle ? quiet + 1 : 0;
      Thread.sleep(100);
    }
  }
}
