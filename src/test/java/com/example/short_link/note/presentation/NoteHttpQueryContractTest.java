package com.example.short_link.note.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.application.dto.OgMetadata;
import com.example.short_link.note.application.write.NotePollService;
import com.example.short_link.note.application.write.NoteScheduleService;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

class NoteHttpQueryContractTest extends OperationalHttpJourneySupport {

  @MockitoBean private ObjectStorage objectStorage;
  @Autowired private NotePollService polls;
  @Autowired private NoteScheduleService schedules;

  @Test
  void repostsByPeopleYouFollowFlowIntoTheFollowingFeedOnceButNeverFromSomeoneYouBlocked()
      throws Exception {
    Actor reader = actor("feed-reader", false);
    Actor friend = actor("feed-friend", false);
    Actor stranger = actor("feed-stranger", false);
    Actor blocked = actor("feed-blocked", false);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        friend.id());
    jdbc.update(
        "INSERT INTO user_block (blocker_id, blocked_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        blocked.id());
    long friendNote = noteAt(friend, "친구가 쓴 노트", 10);
    long strangerNote = noteAt(stranger, "모르는 사람의 노트", 9);
    long blockedNote = noteAt(blocked, "차단한 사람의 노트", 8);
    repostAt(friend, strangerNote, 3);
    repostAt(friend, blockedNote, 2);
    repostAt(friend, friendNote, 1);

    var feed = step("note-following-reposts", "GET", "/api/v1/notes/following", reader, null, 200);

    List<Long> ids = new ArrayList<>();
    feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    assertThat(ids).containsExactly(friendNote, strangerNote);
    assertThat(feed.path("items").get(0).path("repostedBy").path("username").asText())
        .isEqualTo(friend.username());
    assertThat(feed.path("items").get(1).path("repostedBy").path("username").asText())
        .isEqualTo(friend.username());
    assertThat(feed.path("items").get(1).path("author").path("username").asText())
        .isEqualTo(stranger.username());
    step(
        "note-history-blocked",
        "GET",
        "/api/v1/public/notes/" + blockedNote + "/history",
        reader,
        null,
        404);
  }

  @Test
  void aReaderHidesOnePersonsRepostsThenEveryRepostInTheFollowingFeed() throws Exception {
    Actor reader = actor("hide-reader", false);
    Actor loud = actor("hide-loud", false);
    Actor quiet = actor("hide-quiet", false);
    Actor stranger = actor("hide-stranger", false);
    for (Actor followed : List.of(loud, quiet)) {
      jdbc.update(
          "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
          reader.id(),
          followed.id());
    }
    long loudNote = noteAt(loud, "시끄러운 사람의 노트", 10);
    long boostedByLoud = noteAt(stranger, "시끄러운 사람이 퍼간 노트", 9);
    long boostedByQuiet = noteAt(stranger, "조용한 사람이 퍼간 노트", 8);
    repostAt(loud, boostedByLoud, 2);
    repostAt(quiet, boostedByQuiet, 1);

    var hidden =
        step(
            "note-hide-reposts",
            "PUT",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(hidden.path("hidden").asBoolean()).isTrue();
    assertThat(count("note_repost_mute", "user_id = ?", reader.id())).isEqualTo(1);

    var visibility =
        step(
            "note-repost-visibility",
            "GET",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(visibility.path("hidden").asBoolean()).isTrue();

    var withoutLoud =
        step("note-following-hidden-reposts", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(withoutLoud)).containsExactly(boostedByQuiet, loudNote);

    var off =
        step(
            "note-feed-preferences-update",
            "PUT",
            "/api/v1/notes/feed-preferences",
            reader,
            Map.of("showReposts", false),
            200);
    assertThat(off.path("showReposts").asBoolean()).isFalse();

    var preferences =
        step("note-feed-preferences", "GET", "/api/v1/notes/feed-preferences", reader, null, 200);
    assertThat(preferences.path("showReposts").asBoolean()).isFalse();

    var originalsOnly =
        step("note-following-no-reposts", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(originalsOnly)).containsExactly(loudNote);

    var shown =
        step(
            "note-show-reposts",
            "DELETE",
            "/api/v1/notes/repost-visibility/" + loud.username(),
            reader,
            null,
            200);
    assertThat(shown.path("hidden").asBoolean()).isFalse();
    assertThat(count("note_repost_mute", "user_id = ?", reader.id())).isZero();
  }

  @Test
  void hashtagsFileANoteUnderItsTagsAndAFollowedTagBringsItIntoTheFollowingFeed() throws Exception {
    Actor writer = actor("tag-writer", false);
    Actor reader = actor("tag-reader", false);
    jdbc.update(
        "INSERT INTO user_tag_pref (user_id, tag, kind, created_at)"
            + " VALUES (?, '스프링', 'FOLLOW', NOW(6))",
        reader.id());

    long noteId =
        step(
                "note-create-tagged",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "오늘 #스프링 정리, #Kotlin 도 #kotlin 조금"),
                201)
            .path("id")
            .asLong();
    assertThat(
            jdbc.queryForList(
                "SELECT tag FROM note_tag WHERE note_id = ? ORDER BY tag", String.class, noteId))
        .containsExactlyInAnyOrder("스프링", "Kotlin");

    var tagged =
        step(
            "note-tagged",
            "GET",
            "/api/v1/public/notes/tags/%EC%8A%A4%ED%94%84%EB%A7%81",
            null,
            null,
            200);
    assertThat(ids(tagged)).containsExactly(noteId);

    var following =
        step("note-following-tags", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(ids(following)).containsExactly(noteId);
    assertThat(following.path("items").get(0).path("repostedBy").isNull()).isTrue();

    step(
        "note-edit-retag",
        "PATCH",
        "/api/v1/notes/" + noteId,
        writer,
        Map.of("body", "#Kotlin 만 남김"),
        200);
    assertThat(
            jdbc.queryForList("SELECT tag FROM note_tag WHERE note_id = ?", String.class, noteId))
        .containsExactly("Kotlin");
  }

  @Test
  void aMentionedMemberIsToldAndTheMentionLinksOnlyToMembersWhoExist() throws Exception {
    Actor writer = actor("mention-writer", false);
    Actor reader = actor("mention-reader", false);
    String handle = "mr" + reader.id();
    jdbc.update("UPDATE users SET username = ? WHERE id = ?", handle, reader.id());

    var created =
        step(
            "note-create-mention",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of("body", "@" + handle + " 이거 봐요, @nobody_here 는 없는 사람"),
            201);
    long noteId = created.path("id").asLong();
    assertThat(created.path("mentions").get(0).asText()).isEqualTo(handle);
    assertThat(created.path("mentions")).hasSize(1);

    var thread =
        step("note-thread-mention", "GET", "/api/v1/public/notes/" + noteId, null, null, 200);
    assertThat(thread.path("note").path("mentions").get(0).asText()).isEqualTo(handle);
    assertThat(
            count("notification", "recipient_user_id = ? AND type = 'NOTE_MENTION'", reader.id()))
        .isEqualTo(1);
  }

  @Test
  void aContentWarningIsStoredWithTheNoteAndCostsNoQuery() throws Exception {
    Actor writer = actor("warn-writer", false);

    var created =
        step(
            "note-create-warning",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of("body", "결말 이야기", "contentWarning", "스포일러", "sensitive", false),
            201);
    long noteId = created.path("id").asLong();
    assertThat(created.path("contentWarning").asText()).isEqualTo("스포일러");
    assertThat(created.path("sensitive").asBoolean()).isTrue();

    var edited =
        step(
            "note-edit-warning",
            "PATCH",
            "/api/v1/notes/" + noteId,
            writer,
            Map.of("body", "결말 이야기", "contentWarning", ""),
            200);
    assertThat(edited.path("contentWarning").isNull()).isTrue();

    var history =
        step("note-history", "GET", "/api/v1/public/notes/" + noteId + "/history", null, null, 200);
    assertThat(history.path("versions")).hasSize(2);
    assertThat(history.path("versions").get(0).path("contentWarning").isNull()).isTrue();
    assertThat(history.path("versions").get(1).path("contentWarning").asText()).isEqualTo("스포일러");
  }

  @Test
  void anAuthorPinsANoteToTheTopOfTheirProfile() throws Exception {
    Actor writer = actor("pin-writer", false);
    long older = noteAt(writer, "먼저 쓴 노트", 10);
    long newer = noteAt(writer, "나중에 쓴 노트", 1);

    var pinned = step("note-pin", "PUT", "/api/v1/notes/" + older + "/pin", writer, null, 200);
    assertThat(pinned.path("pinned").asBoolean()).isTrue();

    var profile =
        step(
            "note-profile-pinned",
            "GET",
            "/api/v1/public/profiles/" + writer.username() + "/notes",
            null,
            null,
            200);
    assertThat(ids(profile)).containsExactly(older, newer);
    assertThat(profile.path("items").get(0).path("pinned").asBoolean()).isTrue();

    var unpinned =
        step("note-unpin", "DELETE", "/api/v1/notes/" + older + "/pin", writer, null, 200);
    assertThat(unpinned.path("pinned").asBoolean()).isFalse();
  }

  @Test
  void visibilityDecidesWhoReadsANoteOnEveryPath() throws Exception {
    Actor author = actor("vis-author", false);
    Actor follower = actor("vis-follower", false);
    Actor mentioned = actor("vis-mentioned", false);
    Actor stranger = actor("vis-stranger", false);
    String handle = "vm" + mentioned.id();
    jdbc.update("UPDATE users SET username = ? WHERE id = ?", handle, mentioned.id());
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        follower.id(),
        author.id());

    long pub = post("note-vis-create-public", author, "공개 #visall", "public");
    long unl = post("note-vis-create-unlisted", author, "조용한 공개 #visall", "unlisted");
    long prv = post("note-vis-create-private", author, "팔로워만 @" + handle + " #visall", "private");
    long dm = post("note-vis-create-direct", author, "@" + handle + " 둘만 #visall", "direct");
    List<Long> mine = List.of(pub, unl, prv, dm);

    assertThat(
            only(
                step("note-vis-everyone", "GET", "/api/v1/public/notes?size=50", null, null, 200),
                mine))
        .containsExactly(pub);
    assertThat(
            only(
                step("note-vis-tagged", "GET", "/api/v1/public/notes/tags/visall", null, null, 200),
                mine))
        .containsExactly(pub);
    String profile = "/api/v1/public/profiles/" + author.username() + "/notes";
    assertThat(only(step("note-vis-profile-anonymous", "GET", profile, null, null, 200), mine))
        .containsExactly(unl, pub);
    assertThat(only(step("note-vis-profile-follower", "GET", profile, follower, null, 200), mine))
        .containsExactly(prv, unl, pub);
    assertThat(only(step("note-vis-profile-mentioned", "GET", profile, mentioned, null, 200), mine))
        .containsExactly(prv, unl, pub);
    assertThat(only(step("note-vis-profile-author", "GET", profile, author, null, 200), mine))
        .containsExactly(dm, prv, unl, pub);

    step(
        "note-vis-thread-private-stranger",
        "GET",
        "/api/v1/public/notes/" + prv,
        stranger,
        null,
        404);
    step(
        "note-vis-thread-private-follower",
        "GET",
        "/api/v1/public/notes/" + prv,
        follower,
        null,
        200);
    step(
        "note-vis-thread-direct-follower",
        "GET",
        "/api/v1/public/notes/" + dm,
        follower,
        null,
        404);
    step(
        "note-vis-thread-direct-mentioned",
        "GET",
        "/api/v1/public/notes/" + dm,
        mentioned,
        null,
        200);
    step(
        "note-vis-history-private-anonymous",
        "GET",
        "/api/v1/public/notes/" + prv + "/history",
        null,
        null,
        404);

    assertThat(
            only(
                step(
                    "note-vis-direct-mentioned",
                    "GET",
                    "/api/v1/notes/direct",
                    mentioned,
                    null,
                    200),
                mine))
        .containsExactly(dm);
    assertThat(
            only(
                step(
                    "note-vis-direct-follower", "GET", "/api/v1/notes/direct", follower, null, 200),
                mine))
        .isEmpty();
    assertThat(
            only(
                step(
                    "note-vis-following-follower",
                    "GET",
                    "/api/v1/notes/following",
                    follower,
                    null,
                    200),
                mine))
        .containsExactly(prv, unl, pub);
    assertThat(
            only(
                step(
                    "note-vis-following-mentioned",
                    "GET",
                    "/api/v1/notes/following",
                    mentioned,
                    null,
                    200),
                mine))
        .containsExactly(dm);

    step(
        "note-vis-like-private-stranger",
        "PUT",
        "/api/v1/notes/" + prv + "/like",
        stranger,
        null,
        404);
    step(
        "note-vis-repost-private-follower",
        "PUT",
        "/api/v1/notes/" + prv + "/repost",
        follower,
        null,
        400);
    step(
        "note-vis-reply-direct-follower",
        "POST",
        "/api/v1/notes",
        follower,
        Map.of("body", "끼어들기", "inReplyToId", dm),
        404);
    step("note-vis-ap-private", "GET", "/ap/notes/" + prv, null, null, 404);
    step("note-vis-ap-unlisted", "GET", "/ap/notes/" + unl, null, null, 200);
    assertThat(count("note_recipient", "note_id IN (?, ?)", prv, dm)).isEqualTo(2);
  }

  @Test
  void anOwnerKeepsAListOfPeopleAndReadsTheirNotesThere() throws Exception {
    Actor owner = actor("list-owner", false);
    Actor alice = actor("list-alice", false);
    Actor bob = actor("list-bob", false);
    long aliceNote = noteAt(alice, "리스트에 든 사람의 노트", 2);
    long bobNote = noteAt(bob, "리스트 밖 사람의 노트", 1);

    var created =
        step("note-list-create", "POST", "/api/v1/notes/lists", owner, Map.of("title", "동료"), 201);
    long listId = created.path("id").asLong();
    String list = "/api/v1/notes/lists/" + listId;
    step("note-list-add", "PUT", list + "/members/" + alice.username(), owner, null, 204);

    var mine = step("note-list-mine", "GET", "/api/v1/notes/lists", owner, null, 200);
    assertThat(mine.get(0).path("memberCount").asLong()).isEqualTo(1);
    var members = step("note-list-members", "GET", list + "/members", owner, null, 200);
    assertThat(members.get(0).path("username").asText()).isEqualTo(alice.username());
    var membership =
        step(
            "note-list-membership",
            "GET",
            "/api/v1/notes/list-memberships/" + alice.username(),
            owner,
            null,
            200);
    assertThat(membership.path("listIds").get(0).asLong()).isEqualTo(listId);

    var feed = step("note-list-feed", "GET", list + "/notes", owner, null, 200);
    assertThat(only(feed, List.of(aliceNote, bobNote))).containsExactly(aliceNote);
    step("note-list-feed-stranger", "GET", list + "/notes", bob, null, 404);

    var renamed = step("note-list-rename", "PATCH", list, owner, Map.of("title", "가까운 동료"), 200);
    assertThat(renamed.path("title").asText()).isEqualTo("가까운 동료");
    step("note-list-remove", "DELETE", list + "/members/" + alice.username(), owner, null, 204);
    step("note-list-delete", "DELETE", list, owner, null, 204);
    assertThat(count("note_list", "id = ?", listId)).isZero();
  }

  @Test
  void aPollTakesOneVoteEachAndTellsItsVotersWhenItEnds() throws Exception {
    Actor author = actor("poll-author", false);
    Actor voter = actor("poll-voter", false);
    var created =
        step(
            "note-create-poll",
            "POST",
            "/api/v1/notes",
            author,
            Map.of(
                "body",
                "점심 어디서 먹을까?",
                "poll",
                Map.of("options", List.of("국밥", "파스타", "샐러드"), "expiresIn", 3600)),
            201);
    long noteId = created.path("id").asLong();
    assertThat(created.path("poll").path("options").size()).isEqualTo(3);
    assertThat(created.path("poll").path("voted").asBoolean()).isTrue();
    String votes = "/api/v1/notes/" + noteId + "/poll/votes";

    var voted = step("note-poll-vote", "POST", votes, voter, Map.of("choices", List.of(1)), 200);
    assertThat(voted.path("ownVotes").get(0).asInt()).isEqualTo(1);
    assertThat(voted.path("options").get(1).path("votesCount").asLong()).isEqualTo(1);
    step("note-poll-vote-again", "POST", votes, voter, Map.of("choices", List.of(0)), 409);
    step("note-poll-vote-own", "POST", votes, author, Map.of("choices", List.of(0)), 400);

    var thread = step("note-thread-poll", "GET", "/api/v1/public/notes/" + noteId, null, null, 200);
    assertThat(thread.path("note").path("poll").path("votesCount").asLong()).isEqualTo(1);
    assertThat(thread.path("note").path("poll").path("voted").isNull()).isTrue();

    jdbc.update("UPDATE note SET poll_expires_at = '2000-01-01' WHERE id = ?", noteId);
    step("note-poll-vote-ended", "POST", votes, author, Map.of("choices", List.of(0)), 400);
    polls.closeDue();
    assertThat(count("note", "id = ? AND poll_closed_at IS NOT NULL", noteId)).isEqualTo(1);
    long told = 0;
    for (int i = 0; i < 50 && told < 2; i++) {
      Thread.sleep(100);
      told =
          count(
              "notification",
              "type = 'NOTE_POLL' AND recipient_user_id IN (?, ?)",
              author.id(),
              voter.id());
    }
    assertThat(told).isEqualTo(2);
    jdbc.update("DELETE FROM note WHERE id = ?", noteId);
  }

  @Test
  void aMutedPersonLeavesSharedFeedsAndThreadsAndTheirNoticesStopUntilUnmuted() throws Exception {
    Actor reader = actor("mute-reader", false);
    Actor loud = actor("mute-loud", false);
    Actor calm = actor("mute-calm", false);
    long loudNote = noteAt(loud, "뮤트될 사람의 노트", 1);
    long calmNote = noteAt(calm, "조용한 사람의 노트", 2);
    long mine = noteAt(reader, "내 노트", 3);

    var muted =
        step(
            "user-mute",
            "PUT",
            "/api/v1/users/" + loud.username() + "/mute",
            reader,
            Map.of("notifications", true),
            200);
    assertThat(muted.path("muted").asBoolean()).isTrue();
    assertThat(
            step(
                    "user-mute-status",
                    "GET",
                    "/api/v1/users/" + loud.username() + "/mute",
                    reader,
                    null,
                    200)
                .path("notifications")
                .asBoolean())
        .isTrue();
    assertThat(
            step("user-mutes", "GET", "/api/v1/users/me/mutes", reader, null, 200)
                .get(0)
                .path("username")
                .asText())
        .isEqualTo(loud.username());

    var everyone = step("note-everyone-muted", "GET", "/api/v1/public/notes", reader, null, 200);
    assertThat(only(everyone, List.of(loudNote, calmNote))).containsExactly(calmNote);

    step(
        "note-reply-muted",
        "POST",
        "/api/v1/notes",
        loud,
        Map.of("body", "뮤트된 사람의 답글", "inReplyToId", mine),
        201);
    var thread =
        step("note-thread-muted", "GET", "/api/v1/public/notes/" + mine, reader, null, 200);
    assertThat(thread.path("replies").size()).isZero();
    assertThat(
            step(
                    "note-history-muted",
                    "GET",
                    "/api/v1/public/notes/" + loudNote + "/history",
                    reader,
                    null,
                    200)
                .path("versions"))
        .hasSize(1);
    Thread.sleep(500);
    assertThat(
            count(
                "notification",
                "recipient_user_id = ? AND actor_user_id = ?",
                reader.id(),
                loud.id()))
        .isZero();

    step("user-unmute", "DELETE", "/api/v1/users/" + loud.username() + "/mute", reader, null, 204);
    assertThat(count("user_mute", "user_id = ?", reader.id())).isZero();
  }

  @Test
  void aThreadsWriterLimitsWhoRepliesHidesAReplyAndRemovesAnother() throws Exception {
    Actor writer = actor("rc-writer", false);
    Actor named = actor("rc-named", false);
    Actor stranger = actor("rc-stranger", false);
    String handle = "rcn" + named.id();
    jdbc.update("UPDATE users SET username = ? WHERE id = ?", handle, named.id());

    long root =
        step(
                "note-create-reply-limited",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "@" + handle + " 에게만 묻습니다", "replyPolicy", "mentioned"),
                201)
            .path("id")
            .asLong();
    step(
        "note-reply-restricted",
        "POST",
        "/api/v1/notes",
        stranger,
        Map.of("body", "저도요", "inReplyToId", root),
        403);
    long fromNamed =
        step(
                "note-reply-named",
                "POST",
                "/api/v1/notes",
                named,
                Map.of("body", "네, 저요", "inReplyToId", root),
                201)
            .path("id")
            .asLong();
    var closed =
        step(
            "note-thread-reply-limited",
            "GET",
            "/api/v1/public/notes/" + root,
            stranger,
            null,
            200);
    assertThat(closed.path("note").path("replyPolicy").asText()).isEqualTo("mentioned");
    assertThat(closed.path("note").path("canReply").asBoolean()).isFalse();

    var opened =
        step(
            "note-reply-policy-change",
            "PUT",
            "/api/v1/notes/" + root + "/reply-policy",
            writer,
            Map.of("replyPolicy", "everyone"),
            200);
    assertThat(opened.path("replyPolicy").asText()).isEqualTo("everyone");
    assertThat(count("note", "id = ? AND reply_policy = 'EVERYONE'", fromNamed)).isEqualTo(1);
    long fromStranger =
        step(
                "note-reply-opened",
                "POST",
                "/api/v1/notes",
                stranger,
                Map.of("body", "이제 저도", "inReplyToId", root),
                201)
            .path("id")
            .asLong();

    step("note-reply-hide", "PUT", "/api/v1/notes/" + fromStranger + "/hidden", writer, null, 200);
    var thread =
        step("note-thread-hidden-reply", "GET", "/api/v1/public/notes/" + root, null, null, 200);
    List<Long> shown = new ArrayList<>();
    thread.path("replies").forEach(reply -> shown.add(reply.path("id").asLong()));
    assertThat(shown).containsExactly(fromNamed);
    var hidden =
        step(
            "note-hidden-replies",
            "GET",
            "/api/v1/public/notes/" + root + "/hidden-replies",
            null,
            null,
            200);
    assertThat(hidden.get(0).path("id").asLong()).isEqualTo(fromStranger);
    assertThat(hidden.get(0).path("hidden").asBoolean()).isTrue();
    step(
        "note-reply-unhide",
        "DELETE",
        "/api/v1/notes/" + fromStranger + "/hidden",
        writer,
        null,
        200);
    assertThat(count("note", "id = ? AND reply_hidden_at IS NULL", fromStranger)).isEqualTo(1);

    step(
        "note-reply-removed-by-thread-writer",
        "DELETE",
        "/api/v1/notes/" + fromNamed,
        writer,
        null,
        204);
    assertThat(count("note", "id = ?", fromNamed)).isZero();
  }

  @Test
  void aMutedConversationStopsItsNoticesWhileTheThreadStaysReadable() throws Exception {
    Actor writer = actor("conv-writer", false);
    Actor talker = actor("conv-talker", false);
    long root = noteAt(writer, "대화의 시작", 1);
    long answer =
        step(
                "note-reply-before-mute",
                "POST",
                "/api/v1/notes",
                talker,
                Map.of("body", "첫 답글", "inReplyToId", root),
                201)
            .path("id")
            .asLong();

    var muted =
        step(
            "note-conversation-mute",
            "PUT",
            "/api/v1/notes/" + answer + "/conversation-mute",
            writer,
            null,
            200);
    assertThat(muted.path("muted").asBoolean()).isTrue();
    var thread =
        step(
            "note-thread-conversation-muted",
            "GET",
            "/api/v1/public/notes/" + root,
            writer,
            null,
            200);
    assertThat(thread.path("note").path("conversationMuted").asBoolean()).isTrue();
    assertThat(thread.path("replies").get(0).path("conversationMuted").asBoolean()).isTrue();

    long before = count("notification", "recipient_user_id = ?", writer.id());
    step(
        "note-reply-conversation-muted",
        "POST",
        "/api/v1/notes",
        talker,
        Map.of("body", "뮤트된 대화의 답글", "inReplyToId", root),
        201);
    step(
        "note-like-conversation-muted",
        "PUT",
        "/api/v1/notes/" + root + "/like",
        talker,
        null,
        200);
    Thread.sleep(500);
    assertThat(count("notification", "recipient_user_id = ?", writer.id())).isEqualTo(before);

    step(
        "note-conversation-unmute",
        "DELETE",
        "/api/v1/notes/" + root + "/conversation-mute",
        writer,
        null,
        200);
    assertThat(count("note_conversation_mute", "user_id = ?", writer.id())).isZero();
  }

  @Test
  void aFollowerWhoRingsTheBellHearsOfEveryNewNoteButNotOfRepliesToOthers() throws Exception {
    Actor writer = actor("bell-writer", false);
    Actor fan = actor("bell-fan", false);
    String bell = "/api/v1/users/" + writer.username() + "/follow/notes";
    step("note-bell-before-follow", "PUT", bell, fan, null, 409);
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        fan.id(),
        writer.id());

    assertThat(step("note-bell-on", "PUT", bell, fan, null, 200).path("notifyNotes").asBoolean())
        .isTrue();
    assertThat(
            step(
                    "note-bell-follow-status",
                    "GET",
                    "/api/v1/users/" + writer.username() + "/follow",
                    fan,
                    null,
                    200)
                .path("notifyNotes")
                .asBoolean())
        .isTrue();

    long noteId =
        step(
                "note-create-bell",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "종을 켠 사람에게 가는 노트"),
                201)
            .path("id")
            .asLong();
    long other = noteAt(fan, "다른 사람의 노트", 1);
    step(
        "note-reply-bell-elsewhere",
        "POST",
        "/api/v1/notes",
        writer,
        Map.of("body", "남의 노트에 단 답글", "inReplyToId", other),
        201);
    assertThat(
            jdbc.queryForList(
                "SELECT JSON_EXTRACT(payload, '$.noteId') FROM notification"
                    + " WHERE recipient_user_id = ? AND type = 'NOTE_POST' AND actor_user_id = ?",
                Long.class,
                fan.id(),
                writer.id()))
        .containsExactly(noteId);

    assertThat(
            step("note-bell-off", "DELETE", bell, fan, null, 200).path("notifyNotes").asBoolean())
        .isFalse();
    assertThat(count("user_follow", "follower_id = ? AND notify_notes", fan.id())).isZero();
  }

  @Test
  void anEditTellsEveryoneWhoRepostedOrQuotedTheNoteButNotItsAuthor() throws Exception {
    Actor writer = actor("edit-writer", false);
    Actor reposter = actor("edit-reposter", false);
    Actor quoter = actor("edit-quoter", false);
    long noteId = noteAt(writer, "고치기 전 문장", 1);
    step(
        "note-repost-before-edit",
        "PUT",
        "/api/v1/notes/" + noteId + "/repost",
        reposter,
        null,
        200);
    step(
        "note-quote-before-edit",
        "POST",
        "/api/v1/notes",
        quoter,
        Map.of("body", "이 문장 좋다", "quotedNoteId", noteId),
        201);
    step(
        "note-repost-own-before-edit",
        "PUT",
        "/api/v1/notes/" + noteId + "/repost",
        writer,
        null,
        200);

    step(
        "note-edit-shared",
        "PATCH",
        "/api/v1/notes/" + noteId,
        writer,
        Map.of("body", "고친 문장"),
        200);

    assertThat(
            jdbc.queryForList(
                "SELECT recipient_user_id FROM notification WHERE type = 'NOTE_EDIT'"
                    + " AND actor_user_id = ? AND JSON_EXTRACT(payload, '$.noteId') = ?"
                    + " ORDER BY recipient_user_id",
                Long.class,
                writer.id(),
                noteId))
        .containsExactly(reposter.id(), quoter.id());
    jdbc.update("DELETE FROM note WHERE id = ? OR quoted_note_id = ?", noteId, noteId);
  }

  @Test
  void aWriterSchedulesNotesThatPostWhenDue() throws Exception {
    Actor writer = actor("schedule-writer", false);
    String later = Instant.now().plus(Duration.ofHours(3)).toString();
    step(
        "note-schedule-too-soon",
        "POST",
        "/api/v1/notes/scheduled",
        writer,
        Map.of(
            "note",
            Map.of("body", "곧"),
            "scheduledAt",
            Instant.now().plus(Duration.ofMinutes(1)).toString()),
        422);
    long first =
        step(
                "note-schedule",
                "POST",
                "/api/v1/notes/scheduled",
                writer,
                Map.of("note", Map.of("body", "예약한 노트 #아침"), "scheduledAt", later),
                201)
            .path("id")
            .asLong();
    long second =
        step(
                "note-schedule-another",
                "POST",
                "/api/v1/notes/scheduled",
                writer,
                Map.of(
                    "note",
                    Map.of("body", "취소할 노트", "visibility", "PRIVATE"),
                    "scheduledAt",
                    later),
                201)
            .path("id")
            .asLong();
    assertThat(
            step("note-schedules", "GET", "/api/v1/notes/scheduled", writer, null, 200)
                .findValues("id")
                .stream()
                .map(id -> id.asLong())
                .toList())
        .containsExactly(first, second);
    step(
        "note-schedule-move",
        "PATCH",
        "/api/v1/notes/scheduled/" + first,
        writer,
        Map.of("scheduledAt", Instant.now().plus(Duration.ofHours(1)).toString()),
        200);

    jdbc.update(
        "UPDATE note_schedule SET publish_at = publish_at - INTERVAL 1 DAY WHERE id = ?", first);
    assertThat(schedules.publishDue()).isEqualTo(1);
    awaitAsyncWork();
    assertThat(count("note", "user_id = ? AND body = '예약한 노트 #아침'", writer.id())).isEqualTo(1);
    assertThat(count("note_schedule", "id = ?", first)).isZero();

    step("note-schedule-cancel", "DELETE", "/api/v1/notes/scheduled/" + second, writer, null, 204);
    assertThat(count("note_schedule", "user_id = ?", writer.id())).isZero();
  }

  @Test
  void aReaderWhoChoseLanguagesSeesAllNotesInThemAndNotesWithNone() throws Exception {
    Actor writer = actor("lang-writer", false);
    Actor reader = actor("lang-reader", false);
    long japanese =
        step(
                "note-create-language",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "こんにちは", "language", "JA"),
                201)
            .path("id")
            .asLong();
    long english = noteAt(writer, "hello", 0);
    jdbc.update("UPDATE note SET language = 'en' WHERE id = ?", english);
    long unstated = noteAt(writer, "언어를 정하지 않은 노트", 0);

    assertThat(
            step(
                    "note-feed-languages",
                    "PUT",
                    "/api/v1/notes/feed-preferences",
                    reader,
                    Map.of("languages", List.of("ja")),
                    200)
                .path("languages")
                .toString())
        .isEqualTo("[\"ja\"]");
    List<Long> shown = new ArrayList<>();
    step("note-everyone-languages", "GET", "/api/v1/public/notes", reader, null, 200)
        .path("items")
        .forEach(item -> shown.add(item.path("id").asLong()));
    assertThat(shown).contains(japanese, unstated).doesNotContain(english);
    assertThat(
            jdbc.queryForObject("SELECT language FROM note WHERE id = ?", String.class, japanese))
        .isEqualTo("ja");
    jdbc.update("DELETE FROM note WHERE id IN (?, ?, ?)", japanese, english, unstated);
  }

  @Test
  void anOwnerKeepsKeywordFiltersThatTheirAppsApply() throws Exception {
    Actor owner = actor("filter-owner", false);
    Actor stranger = actor("filter-stranger", false);
    var created =
        step(
            "note-filter-create",
            "POST",
            "/api/v1/notes/filters",
            owner,
            Map.of("phrase", "스포일러", "context", List.of("home", "thread"), "expiresIn", 86400),
            201);
    long filterId = created.path("id").asLong();
    assertThat(created.path("action").asText()).isEqualTo("warn");
    String filter = "/api/v1/notes/filters/" + filterId;

    var mine = step("note-filters", "GET", "/api/v1/notes/filters", owner, null, 200);
    assertThat(mine.get(0).path("phrase").asText()).isEqualTo("스포일러");
    var updated =
        step(
            "note-filter-update",
            "PUT",
            filter,
            owner,
            Map.of("phrase", "결말", "context", List.of("public"), "action", "hide"),
            200);
    assertThat(updated.path("expiresAt").isNull()).isTrue();
    step(
        "note-filter-update-stranger",
        "PUT",
        filter,
        stranger,
        Map.of("phrase", "x", "context", List.of("home")),
        404);
    step("note-filter-delete", "DELETE", filter, owner, null, 204);
    assertThat(count("note_filter", "user_id = ?", owner.id())).isZero();
  }

  @Test
  void anyoneFindsPublicNotesByWordsInThem() throws Exception {
    Actor writer = actor("search-writer", false);
    long found = noteAt(writer, "검색되는 헥사고날 포트 이야기", 1);
    long quiet = post("note-search-seed-private", writer, "팔로워만 보는 헥사고날 포트", "private");

    var hits = step("note-search", "GET", "/api/v1/public/notes/search?q=헥사고날", null, null, 200);
    assertThat(only(hits, List.of(found, quiet))).containsExactly(found);
    var short_ =
        step("note-search-short", "GET", "/api/v1/public/notes/search?q=포", null, null, 200);
    assertThat(only(short_, List.of(found, quiet))).containsExactly(found);
  }

  private long post(String id, Actor author, String body, String visibility) throws Exception {
    return step(
            id,
            "POST",
            "/api/v1/notes",
            author,
            Map.of("body", body, "visibility", visibility),
            201)
        .path("id")
        .asLong();
  }

  private static List<Long> only(JsonNode feed, List<Long> among) {
    return ids(feed).stream().filter(among::contains).toList();
  }

  private static List<Long> ids(JsonNode feed) {
    List<Long> ids = new ArrayList<>();
    feed.path("items").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  @Test
  void aHashtagTwoMembersUsedThisWeekTrendsWithItsDailyCounts() throws Exception {
    Actor first = actor("trend-a", false);
    Actor second = actor("trend-b", false);
    String tag = "trend" + UUID.randomUUID().toString().substring(0, 8);
    String lonely = tag + "solo";
    List<Long> ids = new ArrayList<>();
    ids.add(noteAt(first, "#" + tag, 60 * 60));
    ids.add(noteAt(second, "#" + tag, 10));
    ids.add(noteAt(second, "#" + tag + " again", 5));
    for (long id : ids) {
      jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, ?)", id, tag);
    }
    long solo = noteAt(first, "#" + lonely, 3);
    jdbc.update("INSERT INTO note_tag (note_id, tag) VALUES (?, ?)", solo, lonely);
    ids.add(solo);
    try {
      var trends =
          step("note-trending-tags", "GET", "/api/v1/public/notes/trending-tags", null, null, 200);
      JsonNode mine = null;
      List<String> tags = new ArrayList<>();
      for (JsonNode trend : trends) {
        tags.add(trend.path("tag").asString());
        if (trend.path("tag").asString().equals(tag)) {
          mine = trend;
        }
      }
      assertThat(tags).contains(tag).doesNotContain(lonely);
      assertThat(mine.path("accounts").asLong()).isEqualTo(2);
      assertThat(mine.path("uses").asLong()).isEqualTo(3);
      List<Long> history = new ArrayList<>();
      mine.path("history").forEach(day -> history.add(day.asLong()));
      assertThat(history).containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 2L);
    } finally {
      for (long id : ids) {
        jdbc.update("DELETE FROM note_tag WHERE note_id = ?", id);
        jdbc.update("DELETE FROM note WHERE id = ?", id);
      }
    }
  }

  @Test
  void aLinkTwoMembersSharedThisWeekTrendsAndOpensTheNotesThatCarriedIt() throws Exception {
    Actor first = actor("link-a", false);
    Actor second = actor("link-b", false);
    String url = "https://example.com/" + UUID.randomUUID().toString().substring(0, 8);
    String lonely = url + "/solo";
    List<Long> ids = new ArrayList<>();
    ids.add(noteAt(first, "읽어 볼 것 " + url, 60 * 60));
    ids.add(noteAt(second, "이것도 " + url, 10));
    for (long id : ids) {
      jdbc.update(
          "INSERT INTO note_link_preview (note_id, url, title, fetched_at)"
              + " VALUES (?, ?, 'A long read', NOW(6))",
          id,
          url);
    }
    long solo = noteAt(first, lonely, 3);
    jdbc.update(
        "INSERT INTO note_link_preview (note_id, url, title, fetched_at) VALUES (?, ?, 'Solo', NOW(6))",
        solo,
        lonely);
    ids.add(solo);
    try {
      var trends =
          step(
              "note-trending-links", "GET", "/api/v1/public/notes/trending-links", null, null, 200);
      JsonNode mine = null;
      List<String> urls = new ArrayList<>();
      for (JsonNode trend : trends) {
        urls.add(trend.path("url").asString());
        if (trend.path("url").asString().equals(url)) {
          mine = trend;
        }
      }
      assertThat(urls).contains(url).doesNotContain(lonely);
      assertThat(mine.path("title").asString()).isEqualTo("A long read");
      assertThat(mine.path("accounts").asLong()).isEqualTo(2);
      List<Long> history = new ArrayList<>();
      mine.path("history").forEach(day -> history.add(day.asLong()));
      assertThat(history).containsExactly(0L, 0L, 0L, 0L, 1L, 0L, 1L);

      var linked =
          step(
              "note-linked-notes",
              "GET",
              "/api/v1/public/notes/links?url="
                  + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8),
              null,
              null,
              200);
      assertThat(ids(linked)).containsExactly(ids.get(1), ids.get(0));
    } finally {
      for (long id : ids) {
        jdbc.update("DELETE FROM note WHERE id = ?", id);
      }
    }
  }

  private long noteAt(Actor author, String body, int minutesAgo) {
    jdbc.update(
        "INSERT INTO note (user_id, body, created_at)"
            + " VALUES (?, ?, NOW(6) - INTERVAL ? MINUTE)",
        author.id(),
        body,
        minutesAgo);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM note WHERE user_id = ?", Long.class, author.id());
  }

  private void repostAt(Actor reposter, long noteId, int minutesAgo) {
    jdbc.update(
        "INSERT INTO note_repost (note_id, user_id, created_at)"
            + " VALUES (?, ?, NOW(6) - INTERVAL ? MINUTE)",
        noteId,
        reposter.id(),
        minutesAgo);
  }

  @Test
  void aWriterPostsRepliesQuotesEditsAndDeletesNotes() throws Exception {
    Actor writer = actor("note-writer", false);
    Actor reader = actor("note-reader", false);
    when(objectStorage.isConfigured()).thenReturn(true);
    when(objectStorage.presignPut(anyString(), anyString(), any()))
        .thenReturn("https://storage.example.test/upload");
    when(objectStorage.objectSize(anyString())).thenReturn(Optional.of(4L));
    jdbc.update(
        "INSERT INTO posts (user_id, slug, title, status, published_at, created_at, updated_at)"
            + " VALUES (?, 'quoted', 'Quoted essay', 'PUBLISHED', NOW(6), NOW(6), NOW(6))",
        writer.id());
    long postId =
        jdbc.queryForObject(
            "SELECT id FROM posts WHERE user_id = ? AND slug = 'quoted'", Long.class, writer.id());
    jdbc.update(
        "INSERT INTO user_follow (follower_id, following_id, created_at) VALUES (?, ?, NOW(6))",
        reader.id(),
        writer.id());

    String key =
        step(
                "note-image-presign",
                "POST",
                "/api/v1/notes/images/presign",
                writer,
                Map.of("contentType", "image/png"),
                200)
            .path("key")
            .asText();
    assertThat(key).startsWith("note-images/" + writer.id() + "/").endsWith(".png");

    var note =
        step(
            "note-create",
            "POST",
            "/api/v1/notes",
            writer,
            Map.of(
                "body",
                "첫 노트",
                "images",
                List.of(Map.of("key", key, "altText", "창밖 풍경")),
                "quotedPostId",
                postId),
            201);
    long noteId = note.path("id").asLong();
    assertThat(note.path("media").get(0).path("altText").asText()).isEqualTo("창밖 풍경");
    assertThat(note.path("quotedPost").path("slug").asText()).isEqualTo("quoted");
    assertThat(note.path("likeCount").asLong()).isZero();
    var postQuotes =
        step(
            "post-note-quotes",
            "GET",
            "/api/v1/public/posts/" + postId + "/quotes",
            null,
            null,
            200);
    assertThat(postQuotes.path("total").asLong()).isEqualTo(1);
    assertThat(postQuotes.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    verify(objectStorage).applyImmutableCacheControl(key);

    long replyId =
        step(
                "note-reply",
                "POST",
                "/api/v1/notes",
                reader,
                Map.of("body", "답글", "inReplyToId", noteId),
                201)
            .path("id")
            .asLong();

    step("note-like", "PUT", "/api/v1/notes/" + noteId + "/like", reader, null, 200);

    var repost =
        step("note-repost", "PUT", "/api/v1/notes/" + noteId + "/repost", reader, null, 200);
    assertThat(repost.path("reposted").asBoolean()).isTrue();
    assertThat(repost.path("repostCount").asLong()).isEqualTo(1);

    var quote =
        step(
            "note-quote",
            "POST",
            "/api/v1/notes",
            reader,
            Map.of("body", "인용", "quotedNoteId", noteId),
            201);
    long quoteId = quote.path("id").asLong();
    assertThat(quote.path("quotedNote").path("id").asLong()).isEqualTo(noteId);
    assertThat(quote.path("quotedNote").path("author").path("username").asText())
        .isEqualTo(writer.username());
    assertThat(quote.path("quotedNote").path("media").get(0).path("altText").asText())
        .isEqualTo("창밖 풍경");

    var reposts =
        step(
            "note-reposts",
            "GET",
            "/api/v1/public/profiles/" + reader.username() + "/reposts",
            null,
            null,
            200);
    assertThat(reposts.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    assertThat(reposts.path("items").get(0).path("repostCount").asLong()).isEqualTo(1);

    var thread = step("note-thread", "GET", "/api/v1/public/notes/" + noteId, reader, null, 200);
    assertThat(thread.path("note").path("replyCount").asLong()).isEqualTo(1);
    assertThat(thread.path("note").path("likedByMe").asBoolean()).isTrue();
    assertThat(thread.path("note").path("likeCount").asLong()).isEqualTo(1);
    assertThat(thread.path("replies").get(0).path("id").asLong()).isEqualTo(replyId);
    assertThat(thread.path("note").path("quoteCount").asLong()).isEqualTo(1);

    var quotes =
        step("note-quotes", "GET", "/api/v1/public/notes/" + noteId + "/quotes", null, null, 200);
    assertThat(quotes.path("items").get(0).path("id").asLong()).isEqualTo(quoteId);

    var bookmark =
        step("note-bookmark", "PUT", "/api/v1/notes/" + noteId + "/bookmark", reader, null, 200);
    assertThat(bookmark.path("bookmarked").asBoolean()).isTrue();
    var saved = step("note-bookmarks", "GET", "/api/v1/notes/bookmarks", reader, null, 200);
    assertThat(saved.path("items").get(0).path("id").asLong()).isEqualTo(noteId);
    assertThat(saved.path("items").get(0).path("bookmarkedByMe").asBoolean()).isTrue();
    step("note-unbookmark", "DELETE", "/api/v1/notes/" + noteId + "/bookmark", reader, null, 200);
    assertThat(count("note_bookmark", "note_id = ?", noteId)).isZero();

    var mine =
        step(
            "note-profile",
            "GET",
            "/api/v1/public/profiles/" + writer.username() + "/notes",
            writer,
            null,
            200);
    assertThat(mine.path("items").get(0).path("likeCount").asLong()).isEqualTo(1);
    assertThat(mine.path("items").get(0).path("repostCount").asLong()).isEqualTo(1);

    var unrepost =
        step("note-unrepost", "DELETE", "/api/v1/notes/" + noteId + "/repost", reader, null, 200);
    assertThat(unrepost.path("reposted").asBoolean()).isFalse();
    assertThat(count("note_repost", "note_id = ?", noteId)).isZero();

    var following = step("note-following", "GET", "/api/v1/notes/following", reader, null, 200);
    assertThat(following.path("items").get(0).path("id").asLong()).isEqualTo(quoteId);
    assertThat(following.path("items").get(1).path("id").asLong()).isEqualTo(noteId);

    var everyone = step("note-everyone", "GET", "/api/v1/public/notes", null, null, 200);
    assertThat(everyone.path("items").get(0).path("likeCount").asLong()).isZero();
    var trending =
        step("note-trending", "GET", "/api/v1/public/notes?sort=trending&size=50", null, null, 200);
    List<Long> ranked = new ArrayList<>();
    trending.path("items").forEach(item -> ranked.add(item.path("id").asLong()));
    assertThat(ranked).contains(noteId, quoteId).doesNotContain(replyId);
    assertThat(ranked.indexOf(noteId)).isLessThan(ranked.indexOf(quoteId));

    var edited =
        step("note-edit", "PATCH", "/api/v1/notes/" + noteId, writer, Map.of("body", "고친 노트"), 200);
    assertThat(edited.path("body").asText()).isEqualTo("고친 노트");
    assertThat(edited.path("editedAt").isNull()).isFalse();
    step(
        "note-edit-denied",
        "PATCH",
        "/api/v1/notes/" + noteId,
        reader,
        Map.of("body", "남의 노트"),
        403);

    step("note-delete", "DELETE", "/api/v1/notes/" + noteId, writer, null, 204);
    verify(objectStorage, timeout(5_000)).delete(key);
    assertThat(count("note_media", "note_id = ?", noteId)).isZero();
    assertThat(count("note", "id = ? AND in_reply_to_id IS NULL AND reply", replyId)).isEqualTo(1);
    assertThat(count("note", "id = ? AND quoted_note_id IS NULL", quoteId)).isEqualTo(1);
    step("note-thread-gone", "GET", "/api/v1/public/notes/" + noteId, null, null, 404);
    List<Long> readersNotes = new ArrayList<>();
    step(
            "note-profile-orphan",
            "GET",
            "/api/v1/public/profiles/" + reader.username() + "/notes",
            null,
            null,
            200)
        .path("items")
        .forEach(item -> readersNotes.add(item.path("id").asLong()));
    assertThat(readersNotes).containsExactly(quoteId);
    List<Long> rankedAfter = new ArrayList<>();
    step(
            "note-trending-orphan",
            "GET",
            "/api/v1/public/notes?sort=trending&size=50",
            null,
            null,
            200)
        .path("items")
        .forEach(item -> rankedAfter.add(item.path("id").asLong()));
    assertThat(rankedAfter).contains(quoteId).doesNotContain(replyId);

    when(externalMetadata.fetch("https://example.com/essay"))
        .thenReturn(new OgMetadata("An essay", "Why links break", "https://example.com/cover.png"));
    long linkedId =
        step(
                "note-create-link",
                "POST",
                "/api/v1/notes",
                writer,
                Map.of("body", "읽어 볼 글 https://example.com/essay."),
                201)
            .path("id")
            .asLong();
    var linked =
        step("note-thread-link", "GET", "/api/v1/public/notes/" + linkedId, null, null, 200);
    assertThat(linked.path("note").path("linkPreview").path("url").asText())
        .isEqualTo("https://example.com/essay");
    assertThat(linked.path("note").path("linkPreview").path("title").asText())
        .isEqualTo("An essay");
    assertThat(linked.path("note").path("linkPreview").path("image").asText())
        .isEqualTo("https://example.com/cover.png");

    var posted =
        step(
            "note-thread-create",
            "POST",
            "/api/v1/notes/threads",
            writer,
            Map.of(
                "notes",
                List.of(
                    Map.of("body", "이어 쓰기 하나", "contentWarning", "결말 포함"),
                    Map.of("body", "이어 쓰기 둘"),
                    Map.of("body", "이어 쓰기 셋"))),
            201);
    assertThat(posted).hasSize(3);
    long first = posted.get(0).path("id").asLong();
    long second = posted.get(1).path("id").asLong();
    long third = posted.get(2).path("id").asLong();
    assertThat(posted.get(1).path("inReplyToId").asLong()).isEqualTo(first);
    assertThat(
            count(
                "note",
                "id IN (?, ?, ?) AND content_warning = '결말 포함' AND marked_sensitive",
                first,
                second,
                third))
        .isEqualTo(3);

    var parts = step("note-thread-parts", "GET", "/api/v1/public/notes/" + first, null, null, 200);
    List<Long> continuation = new ArrayList<>();
    parts.path("continuation").forEach(part -> continuation.add(part.path("id").asLong()));
    assertThat(continuation).containsExactly(second, third);
    List<Long> replies = new ArrayList<>();
    parts.path("replies").forEach(reply -> replies.add(reply.path("id").asLong()));
    assertThat(replies).doesNotContain(second);

    var feed = step("note-everyone-thread", "GET", "/api/v1/public/notes?size=50", null, null, 200);
    JsonNode head = null;
    for (JsonNode item : feed.path("items")) {
      if (item.path("id").asLong() == first) {
        head = item;
      }
      assertThat(item.path("id").asLong()).isNotEqualTo(replyId);
    }
    assertThat(head).isNotNull();
    assertThat(head.path("thread").path("total").asInt()).isEqualTo(3);
    assertThat(head.path("thread").path("preview").get(0).path("id").asLong()).isEqualTo(second);
  }
}
