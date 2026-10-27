package com.example.short_link.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.short_link.note.application.write.NoteComposer;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.application.write.NoteInteractions;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoteNotificationReadersE2ETest {

  enum Viewer {
    STRANGER,
    FOLLOWER,
    MENTIONED,
    BLOCKED,
    MUTER,
    SHARER,
    ROOMMATE,
    AUTHOR
  }

  enum Vis {
    PUBLIC,
    UNLISTED,
    PRIVATE,
    DIRECT,
    ROOM
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private BlockRepository blocks;
  @Autowired private MuteRepository mutes;
  @Autowired private NoteComposer composer;
  @Autowired private NoteInteractions interactions;
  @Autowired private JdbcTemplate jdbc;

  @Autowired
  @Qualifier("webhookExecutor")
  private ThreadPoolTaskExecutor notices;

  private final Map<Viewer, Long> ids = new EnumMap<>(Viewer.class);
  private final Map<Vis, Long> written = new EnumMap<>(Vis.class);
  private final Map<Vis, String> markers = new EnumMap<>(Vis.class);
  private Long room;

  @AfterEach
  void deleteCommittedRows() {
    if (room != null) {
      jdbc.update("DELETE FROM room WHERE id = ?", room);
    }
    for (Long note : written.values()) {
      jdbc.update("DELETE FROM note_repost WHERE note_id = ?", note);
      jdbc.update("DELETE FROM note_edit WHERE note_id = ?", note);
      jdbc.update("DELETE FROM note_tag WHERE note_id = ?", note);
      jdbc.update("DELETE FROM note WHERE id = ?", note);
    }
    for (Long user : ids.values()) {
      jdbc.update("DELETE FROM notification WHERE recipient_user_id = ?", user);
      jdbc.update("DELETE FROM notification_permission WHERE recipient_user_id = ?", user);
      jdbc.update("DELETE FROM user_follow WHERE follower_id = ? OR following_id = ?", user, user);
      jdbc.update("DELETE FROM user_mute WHERE user_id = ?", user);
      jdbc.update("DELETE FROM user_block WHERE blocker_id = ?", user);
    }
    for (Long user : ids.values()) {
      jdbc.update("DELETE FROM users WHERE id = ?", user);
    }
  }

  @Test
  void aNoticeCarriesANoteOnlyToSomeoneWhoMayReadIt() throws Exception {
    String tag = "nn" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    for (Viewer viewer : Viewer.values()) {
      String handle = tag + viewer.name().substring(0, 2).toLowerCase(Locale.ROOT);
      UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
      user.claimUsername(handle);
      ids.put(viewer, users.save(user).getId());
    }
    long author = ids.get(Viewer.AUTHOR);
    follows.save(new FollowEntity(ids.get(Viewer.FOLLOWER), author));
    follows.save(new FollowEntity(ids.get(Viewer.MUTER), author));
    mutes.save(new UserMuteEntity(ids.get(Viewer.MUTER), author, false, null));
    blocks.save(new UserBlockEntity(author, ids.get(Viewer.BLOCKED)));
    String mentioned = users.findById(ids.get(Viewer.MENTIONED)).orElseThrow().getUsername();
    String roommate = users.findById(ids.get(Viewer.ROOMMATE)).orElseThrow().getUsername();
    jdbc.update(
        "INSERT INTO room (owner_id, name, created_at, updated_at) VALUES (?, ?, NOW(6), NOW(6))",
        author,
        tag);
    room = jdbc.queryForObject("SELECT id FROM room WHERE name = ?", Long.class, tag);
    jdbc.update(
        "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6)), (?, ?, NOW(6))",
        room,
        author,
        room,
        ids.get(Viewer.ROOMMATE));

    for (Vis vis : Vis.values()) {
      String marker = tag + "m" + vis.ordinal() + "q";
      markers.put(vis, marker);
      written.put(
          vis,
          composer
              .create(
                  author,
                  new NoteDraft(
                      marker + " @" + mentioned + (vis == Vis.ROOM ? " @" + roommate : ""),
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      vis.name().toLowerCase(Locale.ROOT),
                      null,
                      null,
                      null,
                      vis == Vis.ROOM ? room : null))
              .id());
    }
    interactions.setRepost(ids.get(Viewer.SHARER), written.get(Vis.PUBLIC), true);
    drain();
    for (Vis vis : Vis.values()) {
      composer.edit(
          author,
          written.get(vis),
          markers.get(vis) + " edited @" + mentioned + (vis == Vis.ROOM ? " @" + roommate : ""),
          null,
          null);
    }
    drain();
    mvc.perform(
        post("/api/v1/notifications/requests/accept")
            .header(
                "Authorization",
                "Bearer " + jwt.createAccessToken(ids.get(Viewer.MENTIONED), "USER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"actorUserId\":" + author + "}"));

    List<String> failures = new ArrayList<>();
    Map<Viewer, Set<Vis>> told = new EnumMap<>(Viewer.class);
    for (Viewer viewer : Viewer.values()) {
      String token = jwt.createAccessToken(ids.get(viewer), "USER");
      Set<Vis> seen = new TreeSet<>();
      for (String path :
          List.of(
              "/api/v1/notifications?limit=50",
              "/api/v1/notifications?limit=50&filter=mentions",
              "/api/v1/notifications/requests")) {
        String body =
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (Vis vis : Vis.values()) {
          if (body.contains(markers.get(vis))) {
            seen.add(vis);
            if (!readable(viewer, vis)) {
              failures.add("LEAK " + viewer + " " + path + " carries the " + vis + " note");
            }
          }
        }
      }
      told.put(viewer, seen);
    }

    assertThat(failures).isEmpty();
    assertThat(told.get(Viewer.MENTIONED))
        .containsExactly(Vis.PUBLIC, Vis.UNLISTED, Vis.PRIVATE, Vis.DIRECT);
    assertThat(told.get(Viewer.ROOMMATE)).contains(Vis.ROOM);
    assertThat(told.get(Viewer.FOLLOWER)).containsExactly(Vis.PUBLIC, Vis.UNLISTED, Vis.PRIVATE);
    assertThat(told.get(Viewer.MUTER)).containsExactly(Vis.PUBLIC, Vis.UNLISTED, Vis.PRIVATE);
    assertThat(told.get(Viewer.SHARER)).containsExactly(Vis.PUBLIC);
    assertThat(told.get(Viewer.STRANGER)).isEmpty();
    assertThat(told.get(Viewer.BLOCKED)).isEmpty();
  }

  private static boolean readable(Viewer viewer, Vis vis) {
    return switch (vis) {
      case PUBLIC, UNLISTED -> true;
      case PRIVATE ->
          viewer == Viewer.FOLLOWER
              || viewer == Viewer.MUTER
              || viewer == Viewer.MENTIONED
              || viewer == Viewer.AUTHOR;
      case DIRECT -> viewer == Viewer.MENTIONED || viewer == Viewer.AUTHOR;
      case ROOM -> viewer == Viewer.ROOMMATE || viewer == Viewer.AUTHOR;
    };
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
