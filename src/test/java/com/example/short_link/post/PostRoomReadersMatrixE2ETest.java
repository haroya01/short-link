package com.example.short_link.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.common.post.PostTitleReader;
import com.example.short_link.post.application.write.RoomPostErasures;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
class PostRoomReadersMatrixE2ETest {

  enum Viewer {
    ANONYMOUS,
    STRANGER,
    FOLLOWER,
    MENTIONED,
    LEFT,
    ROOMMATE,
    AUTHOR
  }

  private static final Set<Viewer> MEMBERS = EnumSet.of(Viewer.ROOMMATE, Viewer.AUTHOR);

  record Seen(int status, String body) {}

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;
  @Autowired private PostTitleReader titles;
  @Autowired private RoomPostErasures erasures;

  private final ObjectMapper json = new ObjectMapper();
  private final Map<Viewer, Long> ids = new EnumMap<>(Viewer.class);
  private final Map<Viewer, String> tokens = new EnumMap<>(Viewer.class);
  private final Map<String, String> markers = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private String tag;
  private String authorName;
  private long room;
  private long otherRoom;
  private long roomPost;
  private String roomSlug;
  private long publicPost;
  private long comment;
  private long highlight;
  private String preview;
  private String seriesSlug;
  private long collection;

  @BeforeEach
  void aRoomPostWithEverythingSaidOnIt() throws Exception {
    tag = word();
    for (Viewer viewer : Viewer.values()) {
      if (viewer == Viewer.ANONYMOUS) {
        continue;
      }
      UserEntity user = user(tag + viewer.name().substring(0, 3).toLowerCase(Locale.ROOT));
      ids.put(viewer, user.getId());
      tokens.put(viewer, jwt.createAccessToken(user.getId(), "USER"));
    }
    long author = ids.get(Viewer.AUTHOR);
    authorName = users.findById(author).orElseThrow().getUsername();
    follows.save(new FollowEntity(ids.get(Viewer.FOLLOWER), author));
    em.flush();
    room = room(author, Viewer.ROOMMATE, Viewer.LEFT);
    otherRoom = room(author);

    for (String key :
        List.of("title", "excerpt", "body", "tag", "comment", "reply", "quote", "note", "answer")) {
      markers.put(key, mark(key));
    }
    roomSlug = tag + "-room";
    publicPost = publish(Viewer.AUTHOR, tag + "-open", "open " + tag, null, null, List.of(tag));
    roomPost =
        publish(
            Viewer.AUTHOR,
            roomSlug,
            markers.get("title"),
            markers.get("excerpt"),
            room,
            List.of(tag, markers.get("tag")));

    for (Viewer viewer : List.of(Viewer.ROOMMATE, Viewer.LEFT)) {
      act(viewer, put("/api/v1/posts/" + roomPost + "/like"));
      act(viewer, put("/api/v1/posts/" + roomPost + "/bookmark"));
      act(viewer, post("/api/v1/posts/" + roomPost + "/read"));
    }
    comment =
        id(
            act(
                Viewer.ROOMMATE,
                post("/api/v1/posts/" + roomPost + "/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            "body",
                            markers.get("comment")
                                + " @"
                                + name(Viewer.MENTIONED)
                                + " @"
                                + name(Viewer.STRANGER)))));
    act(
        Viewer.LEFT,
        post("/api/v1/posts/" + roomPost + "/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("body", markers.get("reply"), "parentId", comment))));
    highlight =
        id(
            act(
                Viewer.LEFT,
                post("/api/v1/posts/" + roomPost + "/highlights")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "blockOrder", 0,
                                "startOffset", 0,
                                "endOffset", 5,
                                "quote", markers.get("quote"),
                                "note", markers.get("note"))))));
    act(
        Viewer.ROOMMATE,
        post("/api/v1/highlights/" + highlight + "/replies")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("body", markers.get("answer"))));
    preview =
        json.readTree(act(Viewer.AUTHOR, post("/api/v1/posts/" + roomPost + "/preview-token")))
            .path("token")
            .asText();

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
    for (long post : List.of(publicPost, roomPost)) {
      jdbc.update(
          "UPDATE posts SET series_id = ?, series_order = ? WHERE id = ?", series, position, post);
      jdbc.update(
          "INSERT INTO series_item (series_id, item_type, ref_id, position, created_at)"
              + " VALUES (?, 'POST', ?, ?, NOW(6))",
          series,
          post,
          position++);
    }
    jdbc.update(
        "INSERT INTO collection (owner_id, title, visibility, created_at, updated_at)"
            + " VALUES (?, ?, 'PUBLIC', NOW(6), NOW(6))",
        author,
        tag + " shelf");
    collection =
        jdbc.queryForObject(
            "SELECT MAX(id) FROM collection WHERE owner_id = ?", Long.class, author);
    jdbc.update(
        "INSERT INTO collection_connection (collection_id, block_type, ref_id, position,"
            + " created_at) VALUES (?, 'POST', ?, 0, NOW(6)), (?, 'HIGHLIGHT', ?, 1, NOW(6)),"
            + " (?, 'POST', ?, 2, NOW(6))",
        collection,
        roomPost,
        collection,
        highlight,
        collection,
        publicPost);
    act(
        Viewer.LEFT,
        post("/api/v1/rooms/" + room + "/leave")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"deleteMyNotes\":false}"));
    em.flush();
    em.clear();
  }

  @Test
  void noReadPathShowsARoomPostOrWhatWasSaidOnItOutsideTheRoom() throws Exception {
    for (Viewer viewer : Viewer.values()) {
      for (String path : paths()) {
        Seen seen = read(viewer, path);
        for (Map.Entry<String, String> marker : markers.entrySet()) {
          if (!MEMBERS.contains(viewer) && seen.body().contains(marker.getValue())) {
            failures.add("LEAK " + viewer + " " + path + " shows the room " + marker.getKey());
          }
        }
      }
    }
    for (String title : titles.findTitlesByIds(List.of(roomPost, publicPost)).values()) {
      if (title.equals(markers.get("title"))) {
        failures.add("LEAK link stats name the room post");
      }
    }

    assertThat(failures).as("%d read paths leaked", failures.size()).isEmpty();
  }

  @Test
  void membersReadItWhereARoomPostBelongsAndNowhereElse() throws Exception {
    String post = "/api/v1/public/profiles/" + authorName + "/posts/" + roomSlug;
    for (Viewer viewer : Viewer.values()) {
      boolean member = MEMBERS.contains(viewer);
      expect(viewer, post, member ? 200 : 404, member, "title", "body");
      expect(viewer, post + "/markdown", member ? 200 : 404, member, "body");
      expect(viewer, "/api/v1/public/posts/" + roomPost + "/comments", 200, member, "comment");
      expect(viewer, "/api/v1/public/posts/" + roomPost + "/highlights", 200, member, "quote");
      expect(viewer, "/api/v1/public/highlights/" + highlight + "/replies", 200, member, "answer");
      expect(viewer, "/api/v1/public/preview/" + preview, 404, false, "title");
      for (String nowhere :
          List.of(
              "/api/v1/public/profiles/" + authorName + "/posts",
              "/api/v1/public/posts?sort=recent&size=50",
              "/api/v1/public/posts?sort=trending&size=50",
              "/api/v1/public/posts?tag=" + tag + "&size=50",
              "/api/v1/public/posts?q=" + tag + "&size=50",
              "/api/v1/public/profiles/" + authorName + "/series/" + seriesSlug,
              "/api/v1/collections/" + collection,
              "/api/v1/public/home?tab=trending&limit=40")) {
        expect(viewer, nowhere, 200, false, "title", "quote");
      }
      expect(viewer, "/api/v1/public/home?tab=latest&limit=40", 200, member, "title");
      if (viewer == Viewer.ANONYMOUS) {
        continue;
      }
      boolean roommate = viewer == Viewer.ROOMMATE;
      expect(viewer, "/api/v1/users/me/likes", 200, roommate, "title");
      expect(viewer, "/api/v1/bookmarks", 200, roommate, "title");
      expect(viewer, "/api/v1/me/saved", 200, roommate, "title");
      expect(viewer, "/api/v1/users/me/reading-history", 200, roommate, "title");
      expect(viewer, "/api/v1/users/me/comments", 200, roommate, "title");
      expect(viewer, "/api/v1/users/me/highlights", 200, false, "title");
      expect(viewer, "/api/v1/feed/following?size=50", 200, false, "title");
      expect(viewer, "/api/v1/public/home?tab=following&limit=40", 200, member, "title");
      expect(viewer, "/api/v1/rooms/" + room + "/items", member ? 200 : 404, member, "title");
      expect(viewer, "/api/v1/rooms/" + room + "/items", member ? 200 : 404, member, "comment");
      expect(viewer, "/api/v1/rooms/items", 200, member, "title");
    }

    assertThat(failures).as("%d paths differ", failures.size()).isEmpty();
  }

  @Test
  void actingOnARoomPostFromOutsideTheRoomIsNotFound() throws Exception {
    for (Viewer viewer : Viewer.values()) {
      if (viewer == Viewer.ANONYMOUS) {
        continue;
      }
      boolean member = MEMBERS.contains(viewer);
      List<MockHttpServletRequestBuilder> acts =
          List.of(
              put("/api/v1/posts/" + roomPost + "/like"),
              put("/api/v1/posts/" + roomPost + "/bookmark"),
              post("/api/v1/posts/" + roomPost + "/comments")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("body", "hi")),
              post("/api/v1/posts/" + roomPost + "/highlights")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"blockOrder\":0,\"startOffset\":0,\"endOffset\":1,\"quote\":\"q\"}"),
              post("/api/v1/highlights/" + highlight + "/replies")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("body", "hi")),
              post("/api/v1/comments/" + comment + "/like"),
              get("/api/v1/posts/" + roomPost + "/like"));
      for (MockHttpServletRequestBuilder act : acts) {
        MvcResult result =
            mvc.perform(act.header("Authorization", "Bearer " + tokens.get(viewer))).andReturn();
        int status = result.getResponse().getStatus();
        String what = result.getRequest().getMethod() + " " + result.getRequest().getRequestURI();
        if (member != (status / 100 == 2) || (!member && status != 404)) {
          failures.add(viewer + " " + what + " -> " + status);
        }
      }
    }

    assertThat(failures).as("%d actions differ", failures.size()).isEmpty();
  }

  @Test
  void aRoomPostGoesOnlyIntoTheWritersRoomAndStaysThere() throws Exception {
    expectError(
        Viewer.STRANGER,
        post("/api/v1/posts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("slug", tag + "-in", "audience", "ROOM", "roomId", room))),
        404,
        "POST_ROOM_NOT_FOUND");
    expectError(
        Viewer.AUTHOR,
        post("/api/v1/posts")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("slug", tag + "-noroom", "audience", "ROOM"))),
        400,
        "POST_ROOM_REQUIRED");
    expectError(
        Viewer.AUTHOR,
        patch("/api/v1/posts/" + publicPost)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("audience", "PUBLIC", "roomId", room))),
        400,
        "POST_ROOM_NOT_ALLOWED");
    expectError(
        Viewer.AUTHOR,
        patch("/api/v1/posts/" + roomPost)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("audience", "ROOM", "roomId", otherRoom))),
        409,
        "POST_ROOM_MOVE");
    expectError(
        Viewer.AUTHOR,
        patch("/api/v1/posts/" + roomPost)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("roomId", otherRoom))),
        409,
        "POST_ROOM_MOVE");
    long draft =
        id(
            act(
                Viewer.ROOMMATE,
                post("/api/v1/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "slug",
                                tag + "-draft",
                                "title",
                                "draft",
                                "audience",
                                "ROOM",
                                "roomId",
                                room)))));
    assertThat(
            json.readTree(read(Viewer.ROOMMATE, "/api/v1/posts/" + draft).body())
                .path("room")
                .path("id")
                .asLong())
        .isEqualTo(room);
    act(
        Viewer.ROOMMATE,
        post("/api/v1/rooms/" + room + "/leave")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"deleteMyNotes\":false}"));
    expectError(
        Viewer.ROOMMATE, post("/api/v1/posts/" + draft + "/publish"), 404, "POST_ROOM_NOT_FOUND");

    assertThat(failures).isEmpty();
  }

  @Test
  void whatWasSaidInTheRoomStaysThereAfterThePostLeavesIt() throws Exception {
    JsonNode exported =
        json.readTree(
            act(
                Viewer.AUTHOR,
                patch("/api/v1/posts/" + roomPost)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"audience\":\"PUBLIC\"}")));
    assertThat(exported.path("audience").asText()).isEqualTo("PUBLIC");
    assertThat(exported.has("room")).isFalse();
    em.flush();
    em.clear();

    for (Viewer viewer : Viewer.values()) {
      boolean member = MEMBERS.contains(viewer);
      expect(
          viewer,
          "/api/v1/public/profiles/" + authorName + "/posts/" + roomSlug,
          200,
          true,
          "title");
      expect(viewer, "/api/v1/public/posts/" + roomPost + "/comments", 200, member, "comment");
      expect(viewer, "/api/v1/public/posts/" + roomPost + "/highlights", 200, member, "quote");
      expect(viewer, "/api/v1/public/highlights/" + highlight + "/replies", 200, member, "answer");
      expect(
          viewer,
          "/api/v1/highlights/feed?scope=global",
          viewer == Viewer.ANONYMOUS ? 401 : 200,
          false,
          "quote");
      expect(viewer, "/api/v1/collections/" + collection, 200, false, "quote");
    }

    assertThat(failures).as("%d paths differ", failures.size()).isEmpty();
  }

  @Test
  void leavingWithMyWritingTakesMyRoomPostsAndWhatISaidOnRoomPosts() throws Exception {
    long mine = publish(Viewer.ROOMMATE, tag + "-mine", "mine " + tag, null, room, List.of(tag));
    act(
        Viewer.ROOMMATE,
        post("/api/v1/posts/" + publicPost + "/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("body", "said openly")));
    act(
        Viewer.ROOMMATE,
        post("/api/v1/rooms/" + room + "/leave")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"deleteMyNotes\":true}"));
    em.flush();
    while (erasures.eraseDue() > 0) {
      em.flush();
    }
    em.clear();

    assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ?", mine)).isZero();
    assertThat(count("SELECT COUNT(*) FROM posts WHERE id = ?", roomPost)).isOne();
    assertThat(
            count(
                "SELECT COUNT(*) FROM comment WHERE user_id = ? AND post_id = ? AND body <> ''",
                ids.get(Viewer.ROOMMATE),
                roomPost))
        .isZero();
    assertThat(
            count("SELECT COUNT(*) FROM comment WHERE id = ? AND deleted_at IS NOT NULL", comment))
        .as("a comment with a reply leaves its place")
        .isOne();
    assertThat(
            count(
                "SELECT COUNT(*) FROM highlight_reply WHERE user_id = ?", ids.get(Viewer.ROOMMATE)))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM comment WHERE user_id = ? AND post_id = ?",
                ids.get(Viewer.ROOMMATE),
                publicPost))
        .isOne();
    assertThat(count("SELECT COUNT(*) FROM room_post_erasure WHERE room_id = ?", room)).isZero();

    act(Viewer.LEFT, delete("/api/v1/rooms/my-posts?roomId=" + room));
    em.flush();
    while (erasures.eraseDue() > 0) {
      em.flush();
    }
    assertThat(count("SELECT COUNT(*) FROM post_highlight WHERE id = ?", highlight)).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM comment WHERE user_id = ? AND body <> ''",
                ids.get(Viewer.LEFT)))
        .isZero();
  }

  private List<String> paths() {
    String post = "/api/v1/public/profiles/" + authorName + "/posts/" + roomSlug;
    return List.of(
        post,
        post + "/markdown",
        "/api/v1/public/profiles/" + authorName,
        "/api/v1/public/profiles/" + authorName + "/posts",
        "/api/v1/public/posts?sort=recent&size=50",
        "/api/v1/public/posts?sort=trending&size=50",
        "/api/v1/public/posts?tag=" + tag + "&size=50",
        "/api/v1/public/posts?tag=" + markers.get("tag") + "&size=50",
        "/api/v1/public/posts?q=" + tag + "&size=50",
        "/api/v1/public/posts?q=" + markers.get("title") + "&size=50",
        "/api/v1/public/feed/trending-by-tag",
        "/api/v1/public/tags?limit=200",
        "/api/v1/public/authors",
        "/api/v1/public/home?tab=latest&limit=40",
        "/api/v1/public/home?tab=trending&limit=40",
        "/api/v1/public/home?tab=following&limit=40",
        "/api/v1/rooms/" + room + "/items",
        "/api/v1/rooms/items",
        "/api/v1/feed/following?size=50",
        "/api/v1/feed/for-you?size=50",
        "/api/v1/public/posts/" + roomPost + "/comments",
        "/api/v1/public/posts/" + roomPost + "/highlights",
        "/api/v1/public/highlights/" + highlight + "/replies",
        "/api/v1/public/posts/" + roomPost + "/quotes?size=50",
        "/api/v1/public/posts/" + roomPost + "/collections",
        "/api/v1/public/highlights/" + highlight + "/collections",
        "/api/v1/posts/" + roomPost + "/like",
        "/api/v1/posts/" + roomPost + "/comments/liked",
        "/api/v1/posts/" + roomPost,
        "/api/v1/posts/" + roomPost + "/markdown",
        "/api/v1/highlights/feed",
        "/api/v1/highlights/feed?scope=global",
        "/api/v1/users/me/likes",
        "/api/v1/bookmarks",
        "/api/v1/me/saved",
        "/api/v1/users/me/reading-history",
        "/api/v1/users/me/comments",
        "/api/v1/users/me/highlights",
        "/api/v1/notifications",
        "/api/v1/collections/" + collection,
        "/api/v1/public/profiles/" + authorName + "/collections",
        "/api/v1/public/feed/connections",
        "/api/v1/feed/connections",
        "/api/v1/public/graph/blocks/post/" + publicPost + "/related",
        "/api/v1/public/profiles/" + authorName + "/series",
        "/api/v1/public/profiles/" + authorName + "/series/" + seriesSlug,
        "/api/v1/public/series",
        "/api/v1/public/preview/" + preview);
  }

  private void expect(Viewer viewer, String path, int status, boolean shown, String... keys)
      throws Exception {
    Seen seen = read(viewer, path);
    if (seen.status() != status) {
      failures.add(viewer + " " + path + " -> " + seen.status() + " instead of " + status);
    }
    for (String key : keys) {
      if (seen.body().contains(markers.get(key)) != shown) {
        failures.add(viewer + " " + path + (shown ? " misses " : " shows ") + "the room " + key);
      }
    }
  }

  private void expectError(
      Viewer viewer, MockHttpServletRequestBuilder request, int status, String code)
      throws Exception {
    MvcResult result =
        mvc.perform(request.header("Authorization", "Bearer " + tokens.get(viewer))).andReturn();
    String body = result.getResponse().getContentAsString();
    if (result.getResponse().getStatus() != status || !body.contains(code)) {
      failures.add(
          viewer
              + " "
              + result.getRequest().getRequestURI()
              + " -> "
              + result.getResponse().getStatus()
              + " "
              + body);
    }
  }

  private long publish(
      Viewer writer, String slug, String title, String excerpt, Long inRoom, List<String> tags)
      throws Exception {
    Map<String, Object> create = new LinkedHashMap<>(Map.of("slug", slug, "title", title));
    if (inRoom != null) {
      create.put("audience", "ROOM");
      create.put("roomId", inRoom);
    }
    long id =
        id(
            act(
                writer,
                post("/api/v1/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(create))));
    String body = inRoom != null ? markers.get("body") + " " + tag : "open body " + tag;
    act(
        writer,
        put("/api/v1/posts/" + id + "/markdown")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("markdown", body + "\n\n" + "words ".repeat(120))));
    Map<String, Object> meta = new LinkedHashMap<>(Map.of("tags", tags));
    if (excerpt != null) {
      meta.put("excerpt", excerpt);
    }
    act(
        writer,
        patch("/api/v1/posts/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(meta)));
    act(writer, post("/api/v1/posts/" + id + "/publish"));
    em.flush();
    return id;
  }

  private String act(Viewer viewer, MockHttpServletRequestBuilder request) throws Exception {
    MvcResult result =
        mvc.perform(request.header("Authorization", "Bearer " + tokens.get(viewer))).andReturn();
    int status = result.getResponse().getStatus();
    if (status / 100 != 2) {
      throw new AssertionError(
          viewer
              + " "
              + result.getRequest().getMethod()
              + " "
              + result.getRequest().getRequestURI()
              + " -> "
              + status
              + " "
              + result.getResponse().getContentAsString());
    }
    return result.getResponse().getContentAsString();
  }

  private Seen read(Viewer viewer, String path) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    if (viewer != Viewer.ANONYMOUS) {
      request.header("Authorization", "Bearer " + tokens.get(viewer));
    }
    MvcResult result = mvc.perform(request).andReturn();
    return new Seen(result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private long id(String body) throws Exception {
    return json.readTree(body).path("id").asLong();
  }

  private String body(String field, String value) throws Exception {
    return json.writeValueAsString(Map.of(field, value));
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private String name(Viewer viewer) {
    return users.findById(ids.get(viewer)).orElseThrow().getUsername();
  }

  private long room(long owner, Viewer... others) {
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        owner,
        tag + " room");
    long id = jdbc.queryForObject("SELECT MAX(id) FROM room WHERE owner_id = ?", Long.class, owner);
    jdbc.update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))", id, owner);
    for (Viewer other : others) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          id,
          ids.get(other));
    }
    return id;
  }

  private String mark(String key) {
    return tag + key.substring(0, 2) + word();
  }

  private UserEntity user(String handle) {
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
