package com.example.short_link.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.example.short_link.note.application.write.RoomNoteErasures;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
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
class RoomE2ETest {

  record Person(long id, String username, String token) {}

  record Answer(int status, String body) {
    <T> T at(String path) {
      return JsonPath.read(body, path);
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private JwtTokenService jwt;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager em;
  @Autowired private RoomNoteErasures erasures;

  private String tag;
  private Person owner;
  private Person friend;
  private Person stranger;

  @BeforeEach
  void people() {
    tag = "rm" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    owner = person("o");
    friend = person("f");
    stranger = person("s");
  }

  @Test
  void aRoomIsMadeJoinedThroughAnInviteAndReadOnlyByItsMembers() throws Exception {
    Answer made = send(owner, post("/api/v1/rooms"), "{\"name\":\"  우리 방  \"}");
    assertThat(made.status()).isEqualTo(201);
    long room = ((Number) made.at("$.id")).longValue();
    assertThat((String) made.at("$.name")).isEqualTo("우리 방");
    assertThat((String) made.at("$.role")).isEqualTo("owner");

    Answer invite = send(owner, post("/api/v1/rooms/" + room + "/invites"), "{}");
    assertThat(invite.status()).isEqualTo(201);
    String code = invite.at("$.code");
    assertThat((String) invite.at("$.pageUrl")).endsWith("/rooms/join/" + code);

    Answer landing = send(null, get("/api/v1/public/room-invites/" + code), null);
    assertThat((String) landing.at("$.roomName")).isEqualTo("우리 방");
    assertThat((String) landing.at("$.inviter.username")).isEqualTo(owner.username());
    assertThat((Boolean) landing.at("$.usable")).isTrue();
    assertThat(send(null, get("/api/v1/public/room-invites/nope" + tag), null).status())
        .isEqualTo(404);

    Answer joined = send(friend, post("/api/v1/room-invites/" + code + "/join"), null);
    assertThat((String) joined.at("$.status")).isEqualTo("joined");
    assertThat(
            (String)
                send(friend, post("/api/v1/room-invites/" + code + "/join"), null).at("$.status"))
        .isEqualTo("already_member");

    long note =
        ((Number)
                send(
                        owner,
                        post("/api/v1/notes"),
                        "{\"body\":\"" + tag + " 방 첫 글\",\"roomId\":" + room + "}")
                    .at("$.id"))
            .longValue();
    assertThat(send(stranger, get("/api/v1/public/notes/" + note), null).status()).isEqualTo(404);
    assertThat(send(stranger, get("/api/v1/rooms/" + room + "/notes"), null).status())
        .isEqualTo(404);
    assertThat(send(stranger, get("/api/v1/rooms/" + room), null).status()).isEqualTo(404);
    Answer seen = send(friend, get("/api/v1/public/notes/" + note), null);
    assertThat((String) seen.at("$.note.visibility")).isEqualTo("room");
    assertThat((String) seen.at("$.note.room.name")).isEqualTo("우리 방");

    Answer list = send(friend, get("/api/v1/rooms"), null);
    assertThat(((Number) list.at("$[0].unreadCount")).intValue()).isEqualTo(1);
    assertThat((String) list.at("$[0].lastNote.excerpt")).startsWith(tag);
    Answer read =
        send(friend, post("/api/v1/rooms/" + room + "/read"), "{\"noteId\":" + note + "}");
    assertThat(((Number) read.at("$.unreadCount")).intValue()).isZero();
    assertThat(
            ((Number) send(friend, get("/api/v1/rooms"), null).at("$[0].unreadCount")).intValue())
        .isZero();

    Answer reply =
        send(friend, post("/api/v1/notes"), "{\"body\":\"답글\",\"inReplyToId\":" + note + "}");
    assertThat((String) reply.at("$.visibility")).isEqualTo("room");
    assertThat(
            send(
                    friend,
                    post("/api/v1/notes"),
                    "{\"body\":\"공개로\",\"inReplyToId\":" + note + ",\"visibility\":\"public\"}")
                .status())
        .isEqualTo(400);
    assertThat(
            send(stranger, post("/api/v1/notes"), "{\"body\":\"끼어들기\",\"roomId\":" + room + "}")
                .status())
        .isEqualTo(404);
    assertThat(
            send(friend, post("/api/v1/notes"), "{\"body\":\"방 없음\",\"visibility\":\"room\"}")
                .status())
        .isEqualTo(400);
    assertThat(send(friend, put("/api/v1/notes/" + note + "/repost"), null).status())
        .isNotEqualTo(200);

    Answer feed = send(friend, get("/api/v1/notes/following"), null);
    assertThat(feed.body()).contains(tag + " 방 첫 글").contains("\"room\":{\"id\":" + room);
    assertThat(
            (Boolean)
                send(friend, put("/api/v1/rooms/" + room + "/feed-hidden"), null)
                    .at("$.hiddenFromFeed"))
        .isTrue();
    assertThat(send(friend, get("/api/v1/notes/following"), null).body())
        .doesNotContain(tag + " 방 첫 글");
    assertThat(send(friend, get("/api/v1/rooms/feed"), null).body()).contains(tag + " 방 첫 글");
    assertThat(send(stranger, get("/api/v1/rooms/feed"), null).body())
        .doesNotContain(tag + " 방 첫 글");
    assertThat(
            (Boolean)
                send(friend, delete("/api/v1/rooms/" + room + "/feed-hidden"), null)
                    .at("$.hiddenFromFeed"))
        .isFalse();
    assertThat(send(friend, get("/api/v1/public/notes?size=50"), null).body())
        .contains(tag + " 방 첫 글");
    assertThat(send(stranger, get("/api/v1/public/notes?size=50"), null).body())
        .doesNotContain(tag + " 방 첫 글");

    Answer detail = send(friend, get("/api/v1/rooms/" + room), null);
    assertThat(((Number) detail.at("$.memberCount")).intValue()).isEqualTo(2);
    assertThat((String) detail.at("$.role")).isEqualTo("member");
  }

  @Test
  void theOwnerRunsTheRoomAndInvitesStopWhenTheySay() throws Exception {
    long room = room(owner);
    String open = code(owner, room, "{\"maxUses\":1}");
    send(friend, post("/api/v1/room-invites/" + open + "/join"), null);
    Answer usedUp = send(stranger, post("/api/v1/room-invites/" + open + "/join"), null);
    assertThat(usedUp.status()).isEqualTo(410);
    assertThat((String) usedUp.at("$.reason")).isEqualTo("used_up");
    assertThat((String) send(null, get("/api/v1/public/room-invites/" + open), null).at("$.reason"))
        .isEqualTo("used_up");

    assertThat(send(friend, patch("/api/v1/rooms/" + room), "{\"name\":\"새 이름\"}").status())
        .isEqualTo(403);
    Answer renamed =
        send(
            owner, patch("/api/v1/rooms/" + room), "{\"name\":\"새 이름\",\"approvalRequired\":true}");
    assertThat((String) renamed.at("$.name")).isEqualTo("새 이름");
    assertThat((Boolean) renamed.at("$.approvalRequired")).isTrue();
    assertThat(send(owner, patch("/api/v1/rooms/" + room), "{\"name\":\"   \"}").status())
        .isEqualTo(400);

    String asked = code(friend, room, "{}");
    Answer requested = send(stranger, post("/api/v1/room-invites/" + asked + "/join"), null);
    assertThat((String) requested.at("$.status")).isEqualTo("requested");
    assertThat(
            (String)
                send(stranger, post("/api/v1/room-invites/" + asked + "/join"), null)
                    .at("$.status"))
        .isEqualTo("requested");
    assertThat(send(friend, get("/api/v1/rooms/" + room + "/requests"), null).status())
        .isEqualTo(403);
    Answer queue = send(owner, get("/api/v1/rooms/" + room + "/requests"), null);
    assertThat((String) queue.at("$[0].person.username")).isEqualTo(stranger.username());
    assertThat(
            send(owner, post("/api/v1/rooms/" + room + "/requests/" + stranger.id()), null)
                .status())
        .isEqualTo(204);
    assertThat(
            send(owner, post("/api/v1/rooms/" + room + "/requests/" + stranger.id()), null)
                .status())
        .isEqualTo(404);

    List<?> ownerSees = send(owner, get("/api/v1/rooms/" + room + "/invites"), null).at("$");
    List<?> friendSees = send(friend, get("/api/v1/rooms/" + room + "/invites"), null).at("$");
    assertThat(ownerSees).hasSize(2);
    assertThat(friendSees).hasSize(1);
    long friendsInvite =
        ((Number) send(friend, get("/api/v1/rooms/" + room + "/invites"), null).at("$[0].id"))
            .longValue();
    assertThat(
            (Boolean)
                send(owner, delete("/api/v1/rooms/" + room + "/invites/" + friendsInvite), null)
                    .at("$.disabled"))
        .isTrue();
    assertThat(
            send(stranger, delete("/api/v1/rooms/" + room + "/invites/" + friendsInvite), null)
                .status())
        .isEqualTo(404);

    assertThat(
            send(owner, delete("/api/v1/rooms/" + room + "/members/" + stranger.id()), null)
                .status())
        .isEqualTo(204);
    String again = code(owner, room, "{}");
    assertThat(send(stranger, post("/api/v1/room-invites/" + again + "/join"), null).status())
        .isEqualTo(403);
    assertThat(
            send(friend, delete("/api/v1/rooms/" + room + "/members/" + owner.id()), null).status())
        .isEqualTo(403);
    assertThat(
            send(owner, delete("/api/v1/rooms/" + room + "/members/" + owner.id()), null).status())
        .isEqualTo(403);

    assertThat(send(owner, post("/api/v1/rooms/" + room + "/invites"), "{\"maxUses\":0}").status())
        .isEqualTo(400);
    assertThat(
            send(owner, post("/api/v1/rooms/" + room + "/invites"), "{\"expiresInSeconds\":10}")
                .status())
        .isEqualTo(400);
    String rejected = code(owner, room, "{\"approval\":true,\"expiresInSeconds\":3600}");
    Person late = person("l");
    send(late, post("/api/v1/room-invites/" + rejected + "/join"), null);
    assertThat(
            send(owner, delete("/api/v1/rooms/" + room + "/requests/" + late.id()), null).status())
        .isEqualTo(204);
    assertThat(
            send(owner, delete("/api/v1/rooms/" + room + "/requests/" + late.id()), null).status())
        .isEqualTo(404);
    jdbc.update(
        "UPDATE room_invite SET expires_at = NOW(6) - INTERVAL 1 DAY WHERE code = ?", rejected);
    em.flush();
    em.clear();
    assertThat(
            (String)
                send(late, post("/api/v1/room-invites/" + rejected + "/join"), null).at("$.reason"))
        .isEqualTo("expired");
  }

  @Test
  void aRoomHoldsFiftyAndAJoinPastThatIsTurnedAway() throws Exception {
    long room = room(owner);
    for (int i = 0; i < 49; i++) {
      jdbc.update(
          "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (?, ?, NOW(6))",
          room,
          person("m" + i).id());
    }
    String code = code(owner, room, "{}");
    Answer full = send(friend, post("/api/v1/room-invites/" + code + "/join"), null);
    assertThat(full.status()).isEqualTo(409);
    assertThat((String) full.at("$.reason")).isEqualTo("full");
    assertThat((String) send(null, get("/api/v1/public/room-invites/" + code), null).at("$.reason"))
        .isEqualTo("full");
    assertThat(
            ((List<?>) send(null, get("/api/v1/public/room-invites/" + code), null).at("$.members"))
                .size())
        .isEqualTo(8);
  }

  @Test
  void leavingKeepsOrTakesTheNotesAndTheOwnerHandsTheRoomOn() throws Exception {
    long room = room(owner);
    send(friend, post("/api/v1/room-invites/" + code(owner, room, "{}") + "/join"), null);
    String friendsCode = code(friend, room, "{}");
    long kept = roomNote(owner, room, "남길 글");
    long gone = roomNote(friend, room, "가져갈 글");

    assertThat(send(owner, post("/api/v1/rooms/" + room + "/leave"), null).status()).isEqualTo(204);
    assertThat(send(owner, get("/api/v1/public/notes/" + kept), null).status()).isEqualTo(200);
    assertThat(send(owner, get("/api/v1/public/notes/" + gone), null).status()).isEqualTo(404);
    assertThat(send(owner, get("/api/v1/rooms/my-notes"), null).body()).contains("남길 글");
    assertThat((String) send(friend, get("/api/v1/rooms/" + room), null).at("$.role"))
        .isEqualTo("owner");
    assertThat(send(owner, post("/api/v1/rooms/" + room + "/leave"), null).status()).isEqualTo(404);
    assertThat(
            send(owner, post("/api/v1/notes"), "{\"body\":\"다시\",\"inReplyToId\":" + kept + "}")
                .status())
        .isEqualTo(404);

    assertThat(
            send(friend, post("/api/v1/rooms/" + room + "/leave"), "{\"deleteMyNotes\":true}")
                .status())
        .isEqualTo(204);
    em.flush();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM note WHERE id = ?", Long.class, gone))
        .isOne();
    erasures.eraseDue();
    em.flush();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM note WHERE id = ?", Long.class, gone))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM room_note_erasure WHERE user_id = ?",
                Long.class,
                friend.id()))
        .isZero();
    em.clear();
    assertThat(
            (Boolean)
                send(null, get("/api/v1/public/room-invites/" + friendsCode), null).at("$.usable"))
        .isFalse();

    assertThat(send(owner, delete("/api/v1/rooms/my-notes?roomId=" + room), null).status())
        .isEqualTo(202);
    erasures.eraseDue();
    em.flush();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM note WHERE id = ?", Long.class, kept))
        .isZero();
  }

  @Test
  void aMemberInvitesPeopleByNameAndTheyAcceptOrDecline() throws Exception {
    long room = room(owner);
    send(friend, post("/api/v1/room-invites/" + code(owner, room, "{}") + "/join"), null);
    Person picked = person("p");
    Person blocker = person("b");
    Person removed = person("r");
    send(removed, post("/api/v1/room-invites/" + code(owner, room, "{}") + "/join"), null);
    send(owner, delete("/api/v1/rooms/" + room + "/members/" + removed.id()), null);
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        blocker.id(),
        friend.id());

    Answer sent =
        send(
            friend,
            post("/api/v1/rooms/" + room + "/invitations"),
            "{\"userIds\":["
                + picked.id()
                + ","
                + stranger.id()
                + ","
                + blocker.id()
                + ","
                + removed.id()
                + ","
                + owner.id()
                + ","
                + picked.id()
                + "]}");
    assertThat(sent.status()).isEqualTo(200);
    List<String> invited = sent.at("$[*].person.username");
    assertThat(invited).containsExactlyInAnyOrder(picked.username(), stranger.username());
    assertThat(
            send(picked, post("/api/v1/rooms/" + room + "/invitations"), "{\"userIds\":[1]}")
                .status())
        .isEqualTo(404);
    assertThat(
            send(friend, post("/api/v1/rooms/" + room + "/invitations"), "{\"userIds\":[]}")
                .status())
        .isEqualTo(400);
    List<?> seen = send(owner, get("/api/v1/rooms/" + room + "/invitations"), null).at("$");
    assertThat(seen).hasSize(2);

    Answer mine = send(picked, get("/api/v1/rooms/invitations"), null);
    assertThat((String) mine.at("$[0].roomName")).isEqualTo(tag);
    assertThat((String) mine.at("$[0].inviter.username")).isEqualTo(friend.username());
    assertThat(((Number) mine.at("$[0].memberCount")).intValue()).isEqualTo(2);
    assertThat((Boolean) mine.at("$[0].approvalRequired")).isFalse();
    assertThat(send(picked, get("/api/v1/rooms/" + room + "/notes"), null).status()).isEqualTo(404);
    Answer accepted = send(picked, post("/api/v1/rooms/invitations/" + room + "/accept"), null);
    assertThat((String) accepted.at("$.status")).isEqualTo("joined");
    assertThat(send(picked, get("/api/v1/rooms/" + room + "/notes"), null).status()).isEqualTo(200);
    assertThat(send(picked, post("/api/v1/rooms/invitations/" + room + "/accept"), null).status())
        .isEqualTo(404);
    List<?> pickedLeft = send(picked, get("/api/v1/rooms/invitations"), null).at("$");
    assertThat(pickedLeft).isEmpty();

    assertThat(
            send(stranger, post("/api/v1/rooms/invitations/" + room + "/decline"), null).status())
        .isEqualTo(204);
    List<?> strangerLeft = send(stranger, get("/api/v1/rooms/invitations"), null).at("$");
    assertThat(strangerLeft).isEmpty();
    List<?> again =
        send(
                owner,
                post("/api/v1/rooms/" + room + "/invitations"),
                "{\"userIds\":[" + stranger.id() + "]}")
            .at("$");
    assertThat(again).isEmpty();
    assertThat(send(stranger, post("/api/v1/rooms/invitations/" + room + "/accept"), null).status())
        .isEqualTo(404);

    Person late = person("l");
    send(
        friend,
        post("/api/v1/rooms/" + room + "/invitations"),
        "{\"userIds\":[" + late.id() + "]}");
    assertThat(
            send(picked, delete("/api/v1/rooms/" + room + "/invitations/" + late.id()), null)
                .status())
        .isEqualTo(404);
    assertThat(
            send(owner, delete("/api/v1/rooms/" + room + "/invitations/" + late.id()), null)
                .status())
        .isEqualTo(204);
    List<?> lateLeft = send(late, get("/api/v1/rooms/invitations"), null).at("$");
    assertThat(lateLeft).isEmpty();

    send(owner, patch("/api/v1/rooms/" + room), "{\"approvalRequired\":true}");
    Person asked = person("a");
    Person welcomed = person("w");
    send(
        friend,
        post("/api/v1/rooms/" + room + "/invitations"),
        "{\"userIds\":[" + asked.id() + "]}");
    send(
        owner,
        post("/api/v1/rooms/" + room + "/invitations"),
        "{\"userIds\":[" + welcomed.id() + "]}");
    assertThat(
            (Boolean)
                send(asked, get("/api/v1/rooms/invitations"), null).at("$[0].approvalRequired"))
        .isTrue();
    assertThat(
            (String)
                send(asked, post("/api/v1/rooms/invitations/" + room + "/accept"), null)
                    .at("$.status"))
        .isEqualTo("requested");
    assertThat(
            (String)
                send(owner, get("/api/v1/rooms/" + room + "/requests"), null)
                    .at("$[0].person.username"))
        .isEqualTo(asked.username());
    assertThat(
            (String)
                send(welcomed, post("/api/v1/rooms/invitations/" + room + "/accept"), null)
                    .at("$.status"))
        .isEqualTo("joined");
  }

  @Test
  void invitationsByNameStopAtFiftyWaiting() throws Exception {
    long room = room(owner);
    for (int i = 0; i < 50; i++) {
      jdbc.update(
          "INSERT INTO room_invitation (room_id, user_id, inviter_id, created_at)"
              + " VALUES (?, ?, ?, NOW(6))",
          room,
          person("q" + i).id(),
          owner.id());
    }
    assertThat(
            send(
                    owner,
                    post("/api/v1/rooms/" + room + "/invitations"),
                    "{\"userIds\":[" + friend.id() + "]}")
                .status())
        .isEqualTo(409);
  }

  @Test
  void mutualFollowsWithoutARoomAreSuggestedUntilWavedOff() throws Exception {
    follows.save(new FollowEntity(owner.id(), friend.id()));
    follows.save(new FollowEntity(friend.id(), owner.id()));
    follows.save(new FollowEntity(owner.id(), stranger.id()));
    List<String> suggested =
        send(owner, get("/api/v1/rooms/suggestions"), null).at("$.friends[*].username");
    assertThat(suggested).containsExactly(friend.username());

    assertThat(send(owner, post("/api/v1/rooms/suggestions/dismiss"), "{\"userIds\":[]}").status())
        .isEqualTo(400);
    send(owner, post("/api/v1/rooms/suggestions/dismiss"), "{\"userIds\":[" + friend.id() + "]}");
    List<?> after = send(owner, get("/api/v1/rooms/suggestions"), null).at("$.friends");
    assertThat(after).isEmpty();
  }

  private long room(Person who) throws Exception {
    return ((Number) send(who, post("/api/v1/rooms"), "{\"name\":\"" + tag + "\"}").at("$.id"))
        .longValue();
  }

  private String code(Person who, long room, String options) throws Exception {
    return send(who, post("/api/v1/rooms/" + room + "/invites"), options).at("$.code");
  }

  private long roomNote(Person who, long room, String body) throws Exception {
    return ((Number)
            send(who, post("/api/v1/notes"), "{\"body\":\"" + body + "\",\"roomId\":" + room + "}")
                .at("$.id"))
        .longValue();
  }

  private Answer send(Person who, MockHttpServletRequestBuilder request, String json)
      throws Exception {
    if (who != null) {
      request.header("Authorization", "Bearer " + who.token());
    }
    if (json != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(json);
    }
    MvcResult result = mvc.perform(request).andReturn();
    return new Answer(result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private Person person(String prefix) {
    String handle = tag + prefix;
    UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
    user.claimUsername(handle);
    long id = users.save(user).getId();
    return new Person(id, handle, jwt.createAccessToken(id, "USER"));
  }
}
