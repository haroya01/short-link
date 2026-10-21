package com.example.short_link.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
class RoomItemsE2ETest {

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private MuteRepository mutes;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;

  private final ObjectMapper json = new ObjectMapper();
  private final Map<String, Long> ids = new LinkedHashMap<>();
  private final Map<String, String> tokens = new LinkedHashMap<>();
  private final Instant base = Instant.now().plus(1, ChronoUnit.HOURS);
  private String tag;
  private long room;
  private long otherRoom;
  private long older;
  private long post;
  private long newer;
  private long elsewhere;

  @BeforeEach
  void aRoomWithNotesAPostAndReplies() throws Exception {
    tag = word();
    for (String who : List.of("ann", "ben", "cat", "out")) {
      UserEntity user = new UserEntity(tag + who + "@x.com", "google", tag + who);
      user.claimUsername(tag + who);
      users.save(user);
      ids.put(who, user.getId());
      tokens.put(who, jwt.createAccessToken(user.getId(), "USER"));
    }
    mutes.save(new UserMuteEntity(ids.get("ben"), ids.get("cat"), false, null));
    em.flush();
    room = room("ann", "ben", "cat");
    otherRoom = room("ben");

    older = note("ann", room, "older note", 1);
    post = roomPost("ben", room, tag + "-post", 2);
    newer = note("cat", room, "newer note", 3);
    elsewhere = note("ben", otherRoom, "other room note", 4);
    for (int i = 0; i < 3; i++) {
      reply(i == 2 ? "cat" : "ann", older, "note reply " + i, 10 + i);
      comment(i == 2 ? "cat" : "ann", post, "post comment " + i, 20 + i);
    }
    em.flush();
  }

  @Test
  void aRoomsNotesAndPostsComeAsOneListNewestFirstWithTheirLatestReplies() throws Exception {
    JsonNode page = read("ann", "/api/v1/rooms/" + room + "/items?limit=20");

    assertThat(kinds(page)).containsExactly("note:" + newer, "post:" + post, "note:" + older);
    assertThat(page.path("next").isNull()).isTrue();
    JsonNode postItem = page.path("items").get(1);
    assertThat(postItem.path("post").path("room").path("id").asLong()).isEqualTo(room);
    assertThat(postItem.path("post").path("audience").asText()).isEqualTo("ROOM");
    assertThat(bodies(postItem)).containsExactly("post comment 1", "post comment 2");
    assertThat(postItem.path("replies").get(1).path("author").path("username").asText())
        .isEqualTo(tag + "cat");
    assertThat(bodies(page.path("items").get(2))).containsExactly("note reply 1", "note reply 2");
    assertThat(page.path("items").get(0).path("note").path("room").path("id").asLong())
        .isEqualTo(room);
    assertThat(page.path("items").get(0).path("replies")).isEmpty();
  }

  @Test
  void itemsAndRepliesLeaveOutWhoTheReaderMuted() throws Exception {
    JsonNode page = read("ben", "/api/v1/rooms/" + room + "/items?limit=20");

    assertThat(kinds(page)).containsExactly("post:" + post, "note:" + older);
    assertThat(bodies(page.path("items").get(0)))
        .containsExactly("post comment 0", "post comment 1");
    assertThat(bodies(page.path("items").get(1))).containsExactly("note reply 0", "note reply 1");
  }

  @Test
  void theCursorWalksBothListsWithoutRepeatingOrSkipping() throws Exception {
    List<String> seen = new ArrayList<>();
    String cursor = null;
    for (int pages = 0; pages < 10; pages++) {
      JsonNode page =
          read(
              "ann",
              "/api/v1/rooms/"
                  + room
                  + "/items?limit=1"
                  + (cursor == null ? "" : "&cursor=" + cursor));
      seen.addAll(kinds(page));
      if (page.path("next").isNull()) {
        break;
      }
      cursor = page.path("next").asText();
    }

    assertThat(seen).containsExactly("note:" + newer, "post:" + post, "note:" + older);
  }

  @Test
  void everyRoomOfTheMemberTogetherAndNothingForOthers() throws Exception {
    assertThat(kinds(read("ben", "/api/v1/rooms/items?limit=20")))
        .containsExactly("note:" + elsewhere, "post:" + post, "note:" + older);
    assertThat(kinds(read("ann", "/api/v1/rooms/items?limit=20")))
        .containsExactly("note:" + newer, "post:" + post, "note:" + older);
    assertThat(kinds(read("out", "/api/v1/rooms/items?limit=20"))).isEmpty();
    MvcResult outsider =
        mvc.perform(
                get("/api/v1/rooms/" + room + "/items")
                    .header("Authorization", "Bearer " + tokens.get("out")))
            .andReturn();
    assertThat(outsider.getResponse().getStatus()).isEqualTo(404);
    assertThat(outsider.getResponse().getContentAsString()).doesNotContain("post comment");
  }

  @Test
  void theHomeFeedCarriesRoomPostsForMembersUntilTheyHideTheRoom() throws Exception {
    for (String tab : List.of("latest", "following")) {
      assertThat(homePosts("ann", tab)).contains(post);
      assertThat(homePosts("out", tab)).doesNotContain(post);
    }
    JsonNode item =
        find(read("ann", "/api/v1/public/home?tab=latest&limit=40").path("items"), post);
    assertThat(item.path("post").path("room").path("name").asText()).isEqualTo(tag + " room");

    act("ann", put("/api/v1/rooms/" + room + "/feed-hidden"));
    em.flush();

    for (String tab : List.of("latest", "following")) {
      assertThat(homePosts("ann", tab)).doesNotContain(post);
    }
    assertThat(kinds(read("ann", "/api/v1/rooms/" + room + "/items"))).contains("post:" + post);
  }

  private List<Long> homePosts(String who, String tab) throws Exception {
    List<Long> found = new ArrayList<>();
    for (JsonNode item : read(who, "/api/v1/public/home?tab=" + tab + "&limit=40").path("items")) {
      if (item.path("kind").asText().equals("post")) {
        found.add(item.path("post").path("id").asLong());
      }
    }
    return found;
  }

  private static JsonNode find(JsonNode items, long postId) {
    for (JsonNode item : items) {
      if (item.path("post").path("id").asLong() == postId) {
        return item;
      }
    }
    throw new AssertionError("post " + postId + " not in " + items);
  }

  private static List<String> kinds(JsonNode page) {
    List<String> kinds = new ArrayList<>();
    for (JsonNode item : page.path("items")) {
      String kind = item.path("kind").asText();
      kinds.add(kind + ":" + item.path(kind).path("id").asLong());
    }
    return kinds;
  }

  private static List<String> bodies(JsonNode item) {
    List<String> bodies = new ArrayList<>();
    item.path("replies").forEach(reply -> bodies.add(reply.path("body").asText()));
    return bodies;
  }

  private long room(String owner, String... others) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        ids.get(owner),
        tag + " room");
    long id =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, ids.get(owner));
    jdbc.update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
        id,
        ids.get(owner));
    for (String other : others) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          id,
          ids.get(other));
    }
    return id;
  }

  private long note(String who, long inRoom, String body, int minute) throws Exception {
    long id =
        json.readTree(
                act(
                    who,
                    post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body, "roomId", inRoom)))))
            .path("id")
            .asLong();
    em.flush();
    jdbc.update("UPDATE note SET created_at = ? WHERE id = ?", at(minute), id);
    return id;
  }

  private void reply(String who, long noteId, String body, int minute) throws Exception {
    long id =
        json.readTree(
                act(
                    who,
                    post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(Map.of("body", body, "inReplyToId", noteId)))))
            .path("id")
            .asLong();
    em.flush();
    jdbc.update("UPDATE note SET created_at = ? WHERE id = ?", at(minute), id);
  }

  private long roomPost(String who, long inRoom, String slug, int minute) throws Exception {
    long id =
        json.readTree(
                act(
                    who,
                    post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            json.writeValueAsString(
                                Map.of(
                                    "slug",
                                    slug,
                                    "title",
                                    slug,
                                    "audience",
                                    "ROOM",
                                    "roomId",
                                    inRoom)))))
            .path("id")
            .asLong();
    act(who, post("/api/v1/posts/" + id + "/publish"));
    em.flush();
    jdbc.update("UPDATE posts SET published_at = ? WHERE id = ?", at(minute), id);
    return id;
  }

  private void comment(String who, long postId, String body, int minute) throws Exception {
    long id =
        json.readTree(
                act(
                    who,
                    post("/api/v1/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body)))))
            .path("id")
            .asLong();
    em.flush();
    jdbc.update("UPDATE comment SET created_at = ? WHERE id = ?", at(minute), id);
  }

  private Timestamp at(int minute) {
    return Timestamp.from(base.plus(minute, ChronoUnit.MINUTES));
  }

  private String act(String who, MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result =
        mvc.perform(request.header("Authorization", "Bearer " + tokens.get(who))).andReturn();
    int status = result.getResponse().getStatus();
    if (status / 100 != 2) {
      throw new AssertionError(
          result.getRequest().getRequestURI()
              + " -> "
              + status
              + " "
              + result.getResponse().getContentAsString());
    }
    return result.getResponse().getContentAsString();
  }

  private JsonNode read(String who, String path) throws Exception {
    return json.readTree(act(who, get(path)));
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
