package com.example.short_link.note.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.note.application.read.NoteFeedView;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteRepliesView;
import com.example.short_link.note.application.read.NoteThreadView;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.PageAsk;
import com.example.short_link.note.application.write.NoteComposer;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.application.write.NoteThreadControls;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.room.application.write.RoomCommandService;
import com.example.short_link.room.domain.repository.RoomRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NoteReplyPagesIntegrationTest {

  @Autowired private NoteComposer composer;
  @Autowired private NoteThreadControls threads;
  @Autowired private NoteQueryService query;
  @Autowired private UserRepository userRepository;
  @Autowired private RoomCommandService rooms;
  @Autowired private RoomRepository roomRepository;
  @PersistenceContext private EntityManager em;

  private long writer;
  private long alice;
  private long bob;

  @BeforeEach
  void aWriterAndTwoRepliers() {
    writer = user("rp_w");
    alice = user("rp_a");
    bob = user("rp_b");
  }

  @Test
  void aThreadAsksForFewerRepliesAndSaysWhereTheRestBegin() {
    long root = post(writer, "물어볼게요", null, null);
    long first = post(alice, "하나", root, null);
    long second = post(bob, "둘", root, null);
    long third = post(alice, "셋", root, null);
    flush();

    NoteThreadView thread = query.thread(null, root, 2);

    assertThat(thread.replies()).extracting(NoteView::id).containsExactly(first, second);
    assertThat(thread.repliesNext()).isEqualTo(String.valueOf(second));
    assertThat(query.thread(null, root, NoteQueryService.MAX_REPLIES).repliesNext()).isNull();

    NoteRepliesView rest = query.replies(null, root, Long.parseLong(thread.repliesNext()), 20);

    assertThat(rest.items()).extracting(NoteView::id).containsExactly(third);
    assertThat(rest.next()).isNull();
    assertThat(rest.latest()).isEqualTo(String.valueOf(third));
    assertThat(rest.continued()).isFalse();
  }

  @Test
  void whatIsNewLeavesOutHiddenRepliesAndTheWritersOwnContinuation() {
    long root = post(writer, "이어 쓸게요", null, null);
    long seen = post(alice, "본 답", root, null);
    long hidden = post(bob, "가릴 답", root, null);
    long continued = post(writer, "제가 이어서", root, null);
    long fresh = post(bob, "새 답", root, null);
    threads.setReplyHidden(writer, hidden, true);
    flush();

    NoteRepliesView newer = query.replies(null, root, seen, 20);

    assertThat(newer.items()).extracting(NoteView::id).containsExactly(fresh);
    assertThat(newer.continued()).isTrue();
    assertThat(newer.latest()).isEqualTo(String.valueOf(fresh));
    assertThat(query.replies(null, root, continued, 20).continued()).isFalse();
  }

  @Test
  void repliesToAThreadTheViewerCannotReadAreNotThere() {
    long root = post(writer, "팔로워만", null, null, "private");
    flush();

    assertThatThrownBy(() -> query.replies(alice, root, 0L, 20)).isInstanceOf(NoteException.class);
  }

  @Test
  void aRoomGivesOnlyNotesNewerThanTheCursorNewestFirst() {
    long room = room(writer, alice);
    long old = roomNote(writer, room, "예전");
    long newer = roomNote(alice, room, "새 글");
    long newest = roomNote(writer, room, "더 새 글");
    flush();

    NoteFeedView fresh = query.room(alice, room, old, new PageAsk(0, 1));

    assertThat(fresh.items()).extracting(NoteView::id).containsExactly(newest);
    assertThat(fresh.hasNext()).isTrue();
    assertThat(query.room(alice, room, old, new PageAsk(0, 20)).items())
        .extracting(NoteView::id)
        .containsExactly(newest, newer);
  }

  private long room(long owner, long member) {
    long room = rooms.create(owner, "실시간").id();
    roomRepository.addMember(room, member, Instant.now());
    return room;
  }

  private long roomNote(long author, long room, String body) {
    return composer
        .create(
            author,
            new NoteDraft(
                body, List.of(), null, null, null, null, false, null, null, null, null, room))
        .id();
  }

  private long post(long author, String body, Long inReplyTo, String policy) {
    return post(author, body, inReplyTo, policy, null);
  }

  private long post(long author, String body, Long inReplyTo, String policy, String visibility) {
    return composer
        .create(
            author,
            new NoteDraft(
                body,
                List.of(),
                null,
                inReplyTo,
                null,
                null,
                false,
                visibility,
                null,
                null,
                policy))
        .id();
  }

  private void flush() {
    em.flush();
    em.clear();
  }

  private long user(String prefix) {
    String handle = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    u.claimUsername(handle);
    return userRepository.save(u).getId();
  }
}
