package com.example.short_link.room.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoomHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Test
  void aRoomIsMadeSharedJoinedReadAndLeft() throws Exception {
    Actor owner = actor("room-owner", false);
    Actor friend = actor("room-friend", false);
    Actor asker = actor("room-asker", false);
    Actor declined = actor("room-declined", false);

    var room = step("room-create", "POST", "/api/v1/rooms", owner, Map.of("name", "우리 방"), 201);
    long roomId = room.path("id").asLong();
    var invite =
        step(
            "room-invite-create",
            "POST",
            "/api/v1/rooms/" + roomId + "/invites",
            owner,
            Map.of(),
            201);
    String code = invite.path("code").asText();
    var landing =
        step("room-invite-landing", "GET", "/api/v1/public/room-invites/" + code, null, null, 200);
    assertThat(landing.path("roomName").asText()).isEqualTo("우리 방");
    var joined =
        step("room-join", "POST", "/api/v1/room-invites/" + code + "/join", friend, null, 200);
    assertThat(joined.path("status").asText()).isEqualTo("joined");

    var note =
        step(
            "room-note-create",
            "POST",
            "/api/v1/notes",
            owner,
            Map.of("body", "방에서 처음 쓰는 글", "roomId", roomId),
            201);
    long noteId = note.path("id").asLong();
    assertThat(note.path("room").path("name").asText()).isEqualTo("우리 방");

    var notes = step("room-notes", "GET", "/api/v1/rooms/" + roomId + "/notes", friend, null, 200);
    assertThat(notes.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    var feed = step("room-feed", "GET", "/api/v1/rooms/feed", friend, null, 200);
    assertThat(feed.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    var following = step("room-following", "GET", "/api/v1/notes/following", friend, null, 200);
    assertThat(following.path("items").get(0).path("room").path("id").asLong()).isEqualTo(roomId);
    var list = step("room-list", "GET", "/api/v1/rooms", friend, null, 200);
    assertThat(list.get(0).path("unreadCount").asLong()).isEqualTo(1);
    var read =
        step(
            "room-read",
            "POST",
            "/api/v1/rooms/" + roomId + "/read",
            friend,
            Map.of("noteId", noteId),
            200);
    assertThat(read.path("unreadCount").asLong()).isZero();
    step("room-detail", "GET", "/api/v1/rooms/" + roomId, friend, null, 200);
    step("room-pin", "PUT", "/api/v1/rooms/" + roomId + "/pin", friend, null, 204);
    step("room-unpin", "DELETE", "/api/v1/rooms/" + roomId + "/pin", friend, null, 204);
    step("room-feed-hide", "PUT", "/api/v1/rooms/" + roomId + "/feed-hidden", friend, null, 200);
    step("room-feed-show", "DELETE", "/api/v1/rooms/" + roomId + "/feed-hidden", friend, null, 200);

    step(
        "room-update",
        "PATCH",
        "/api/v1/rooms/" + roomId,
        owner,
        Map.of("name", "새 이름", "approvalRequired", true),
        200);
    var asked =
        step(
            "room-join-request",
            "POST",
            "/api/v1/room-invites/" + code + "/join",
            asker,
            null,
            200);
    assertThat(asked.path("status").asText()).isEqualTo("requested");
    step(
        "room-join-request-again",
        "POST",
        "/api/v1/room-invites/" + code + "/join",
        declined,
        null,
        200);
    var requests =
        step("room-requests", "GET", "/api/v1/rooms/" + roomId + "/requests", owner, null, 200);
    assertThat(requests).hasSize(2);
    step(
        "room-request-approve",
        "POST",
        "/api/v1/rooms/" + roomId + "/requests/" + asker.id(),
        owner,
        null,
        204);
    step(
        "room-request-reject",
        "DELETE",
        "/api/v1/rooms/" + roomId + "/requests/" + declined.id(),
        owner,
        null,
        204);
    var invites =
        step("room-invites", "GET", "/api/v1/rooms/" + roomId + "/invites", owner, null, 200);
    step(
        "room-invite-disable",
        "DELETE",
        "/api/v1/rooms/" + roomId + "/invites/" + invites.get(0).path("id").asLong(),
        owner,
        null,
        200);
    step(
        "room-member-remove",
        "DELETE",
        "/api/v1/rooms/" + roomId + "/members/" + asker.id(),
        owner,
        null,
        204);

    step(
        "room-note-reply",
        "POST",
        "/api/v1/notes",
        friend,
        Map.of("body", "방 안의 답글", "inReplyToId", noteId),
        201);
    step("room-my-notes", "GET", "/api/v1/rooms/my-notes", friend, null, 200);
    step("room-leave", "POST", "/api/v1/rooms/" + roomId + "/leave", friend, Map.of(), 204);
    step(
        "room-my-notes-delete",
        "DELETE",
        "/api/v1/rooms/my-notes?roomId=" + roomId,
        friend,
        null,
        202);
    assertThat(count("room_note_erasure", "room_id = ? AND user_id = ?", roomId, friend.id()))
        .isOne();
  }

  @Test
  void peopleInvitedByNameAcceptOrDecline() throws Exception {
    Actor owner = actor("room-namer", false);
    Actor picked = actor("room-picked", false);
    Actor other = actor("room-other", false);
    Actor taken = actor("room-taken", false);
    long roomId =
        step("room-create", "POST", "/api/v1/rooms", owner, Map.of("name", "이름 방"), 201)
            .path("id")
            .asLong();

    var sent =
        step(
            "room-invitation-send",
            "POST",
            "/api/v1/rooms/" + roomId + "/invitations",
            owner,
            Map.of("userIds", List.of(picked.id(), other.id(), taken.id())),
            200);
    assertThat(sent).hasSize(3);
    step("room-invitees", "GET", "/api/v1/rooms/" + roomId + "/invitations", owner, null, 200);
    var mine = step("room-invitations-mine", "GET", "/api/v1/rooms/invitations", picked, null, 200);
    assertThat(mine.get(0).path("roomId").asLong()).isEqualTo(roomId);
    var accepted =
        step(
            "room-invitation-accept",
            "POST",
            "/api/v1/rooms/invitations/" + roomId + "/accept",
            picked,
            null,
            200);
    assertThat(accepted.path("status").asText()).isEqualTo("joined");
    step(
        "room-invitation-decline",
        "POST",
        "/api/v1/rooms/invitations/" + roomId + "/decline",
        other,
        null,
        204);
    step(
        "room-invitation-cancel",
        "DELETE",
        "/api/v1/rooms/" + roomId + "/invitations/" + taken.id(),
        owner,
        null,
        204);
  }

  @Test
  void mutualFollowsAreSuggestedForARoomUntilDismissed() throws Exception {
    Actor me = actor("room-nudge", false);
    Actor friend = actor("room-nudge-friend", false);
    for (List<Long> pair : List.of(List.of(me.id(), friend.id()), List.of(friend.id(), me.id()))) {
      jdbc.update(
          "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
          pair.get(0),
          pair.get(1));
    }
    var suggested = step("room-suggestions", "GET", "/api/v1/rooms/suggestions", me, null, 200);
    assertThat(suggested.path("friends").get(0).path("id").asLong()).isEqualTo(friend.id());
    step(
        "room-suggestions-dismiss",
        "POST",
        "/api/v1/rooms/suggestions/dismiss",
        me,
        Map.of("userIds", List.of(friend.id())),
        204);
  }
}
