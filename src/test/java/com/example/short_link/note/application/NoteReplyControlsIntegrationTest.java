package com.example.short_link.note.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.note.application.read.NoteQueryService;
import com.example.short_link.note.application.read.NoteThreadView;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class NoteReplyControlsIntegrationTest {

  @Autowired private NoteCommandService command;
  @Autowired private NoteQueryService query;
  @Autowired private RemoteNotes remoteNotes;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowRepository followRepository;
  @PersistenceContext private EntityManager em;

  private String statuses;
  private long writer;
  private long named;
  private long followed;
  private long stranger;
  private String namedHandle;

  @BeforeEach
  void aWriterAndThreeReaders() {
    statuses = "https://rc.example/s/" + UUID.randomUUID() + "/";
    writer = user("rc_w");
    namedHandle = handle("rc_n");
    named = user(namedHandle, true);
    followed = user("rc_f");
    stranger = user("rc_s");
    followRepository.save(new FollowEntity(writer, followed));
  }

  @Test
  void aLimitedThreadTakesRepliesOnlyFromThoseItLetsInAtEveryDepth() {
    long root = post(writer, "@" + namedHandle + " 와 팔로우한 사람만", null, "following");
    long fromNamed = post(named, "저요", root, null);

    post(followed, "팔로우했어요", fromNamed, null);
    post(writer, "제 스레드예요", fromNamed, null);
    assertRestricted(() -> post(stranger, "저도요", root, null));
    assertRestricted(() -> post(stranger, "깊은 곳에서도", fromNamed, null));

    command.setReplyPolicy(writer, root, "mentioned");
    flush();
    assertThat(policyOf(fromNamed)).isEqualTo("MENTIONED");
    assertRestricted(() -> post(followed, "이제는 못 해요", fromNamed, null));
    post(named, "저는 여전히", fromNamed, null);

    command.setReplyPolicy(writer, root, "everyone");
    flush();
    post(stranger, "이제 누구나", fromNamed, null);
  }

  @Test
  void whoTheFirstNoteNamesIsReadAsItReadsNowAfterAnEdit() {
    String laterHandle = handle("rc_l");
    long later = user(laterHandle, true);
    long root = post(writer, "@" + namedHandle + " 에게만", null, "mentioned");

    command.edit(writer, root, "@" + laterHandle + " 에게만", null, null);
    flush();

    assertRestricted(() -> post(named, "이제 저는 아니죠", root, null));
    post(later, "제가 답할게요", root, null);
    flush();
    assertThat(canReply(root, named)).isFalse();
    assertThat(canReply(root, later)).isTrue();
  }

  @Test
  void aThreadTellsEachViewerWhetherTheyMayReply() {
    long root = post(writer, "@" + namedHandle + " 에게만", null, "mentioned");
    long reply = post(named, "네", root, null);
    flush();

    assertThat(canReply(root, writer)).isTrue();
    assertThat(canReply(root, named)).isTrue();
    assertThat(canReply(root, stranger)).isFalse();
    assertThat(canReply(root, null)).isNull();
    NoteThreadView fromReply = query.thread(reply, stranger);
    assertThat(fromReply.note().replyPolicy()).isEqualTo("mentioned");
    assertThat(fromReply.note().canReply()).isFalse();
    assertThat(query.thread(reply, named).note().canReply()).isTrue();
  }

  @Test
  void aHiddenReplyLeavesTheThreadAndItsCountAndWaitsUnderHiddenReplies() {
    long root = post(writer, "아무나", null, null);
    long kept = post(named, "남는 답글", root, null);
    long hidden = post(stranger, "숨길 답글", root, null);
    long underHidden = post(named, "숨긴 답글의 답글", hidden, null);

    command.setReplyHidden(writer, hidden, true);
    flush();
    assertThat(query.thread(hidden, followed).replies())
        .extracting(NoteView::id)
        .containsExactly(underHidden);

    NoteThreadView thread = query.thread(root, followed);
    assertThat(thread.replies()).extracting(NoteView::id).containsExactly(kept);
    assertThat(thread.note().replyCount()).isEqualTo(1);
    assertThat(thread.hiddenReplyCount()).isEqualTo(1);
    assertThat(query.thread(root, null).hiddenReplyCount()).isEqualTo(1);
    assertThat(query.hiddenReplies(root, followed))
        .extracting(NoteView::id, NoteView::hidden)
        .containsExactly(org.assertj.core.groups.Tuple.tuple(hidden, true));
    assertThat(query.thread(hidden, null).note().hidden()).isTrue();
    assertThatThrownBy(() -> command.setReplyHidden(named, kept, true))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_PERMISSION_DENIED));

    command.setReplyHidden(writer, hidden, false);
    flush();
    assertThat(query.thread(root, null).replies())
        .extracting(NoteView::id)
        .containsExactly(kept, hidden);
    assertThat(query.hiddenReplies(root, null)).isEmpty();
    assertThat(query.thread(root, null).hiddenReplyCount()).isZero();
  }

  @Test
  void aThreadTellsOnlyTheWriterOfItsFirstNoteThatTheyModerateItAtEveryDepth() {
    long root = post(writer, "아무나", null, null);
    long reply = post(named, "답글", root, null);
    long deep = post(followed, "답글의 답글", reply, null);
    long deeper = post(stranger, "더 깊은 답글", deep, null);
    flush();

    for (long note : List.of(root, reply, deep, deeper)) {
      assertThat(query.thread(note, writer).viewerCanModerate()).as("note %d", note).isTrue();
      assertThat(query.thread(note, named).viewerCanModerate()).as("note %d", note).isFalse();
      assertThat(query.thread(note, null).viewerCanModerate()).as("note %d", note).isFalse();
    }
    command.setReplyHidden(writer, deeper, true);
    flush();
    assertThat(query.thread(deep, stranger).hiddenReplyCount()).isEqualTo(1);
    assertThat(query.thread(root, stranger).hiddenReplyCount()).isZero();
  }

  @Test
  void theThreadsWriterDeletesAMembersReplyAndUnhooksOneFromElsewhere() {
    long root = post(writer, "아무나", null, null);
    long members = post(stranger, "회원 답글", root, null);
    long remoteActor = remoteActor("alice");
    long remote = remoteNotes.receive(remoteReply(remoteActor, statuses + "1", root)).orElseThrow();
    flush();
    assertThat(query.thread(root, null).replies())
        .extracting(NoteView::id)
        .containsExactly(members, remote);

    command.delete(writer, members);
    command.delete(writer, remote);
    flush();

    assertThat(count("SELECT COUNT(*) FROM note WHERE id = " + members)).isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM note WHERE id = "
                    + remote
                    + " AND in_reply_to_id IS NULL AND conversation_id IS NULL AND reply"))
        .isEqualTo(1);
    assertThat(query.thread(root, null).replies()).isEmpty();
    assertThat(remoteNotes.receive(remoteReply(remoteActor, statuses + "1", root))).isEmpty();
    assertThatThrownBy(() -> command.delete(named, root))
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_PERMISSION_DENIED));
  }

  @Test
  void anAnswerFromElsewhereToALimitedThreadIsKeptUnattachedUnlessNamedOrFollowed() {
    long bob = remoteActor("bob");
    long carol = remoteActor("carol");
    long dave = remoteActor("dave");
    long root = post(writer, "@bob@rc.example 에게만", null, "following");
    em.createNativeQuery(
            "INSERT INTO federation_following (user_id, remote_actor_id, follow_activity_id,"
                + " accepted_at, created_at, updated_at) VALUES (:u, :a, 'f', NOW(6), NOW(6),"
                + " NOW(6))")
        .setParameter("u", writer)
        .setParameter("a", carol)
        .executeUpdate();

    long fromBob = remoteNotes.receive(remoteReply(bob, statuses + "b", root)).orElseThrow();
    long fromCarol = remoteNotes.receive(remoteReply(carol, statuses + "c", root)).orElseThrow();
    long fromDave = remoteNotes.receive(remoteReply(dave, statuses + "d", root)).orElseThrow();
    flush();

    assertThat(query.thread(root, null).replies())
        .extracting(NoteView::id)
        .containsExactly(fromBob, fromCarol);
    assertThat(
            count(
                "SELECT COUNT(*) FROM note WHERE id = "
                    + fromDave
                    + " AND in_reply_to_id IS NULL AND reply"))
        .isEqualTo(1);
  }

  private interface Write {
    void run();
  }

  private static void assertRestricted(Write write) {
    assertThatThrownBy(write::run)
        .isInstanceOfSatisfying(
            NoteException.class,
            e -> assertThat(e.errorCode()).isEqualTo(NoteErrorCode.NOTE_REPLY_RESTRICTED));
  }

  private long post(long author, String body, Long inReplyTo, String policy) {
    return command
        .create(
            author,
            new NoteDraft(
                body, List.of(), null, inReplyTo, null, null, false, null, null, null, policy))
        .id();
  }

  private Boolean canReply(long noteId, Long viewer) {
    return query.thread(noteId, viewer).note().canReply();
  }

  private String policyOf(long noteId) {
    return (String)
        em.createNativeQuery("SELECT reply_policy FROM note WHERE id = :id")
            .setParameter("id", noteId)
            .getSingleResult();
  }

  private long count(String sql) {
    return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
  }

  private void flush() {
    em.flush();
    em.clear();
  }

  private long remoteActor(String username) {
    String uri =
        "https://rc.example/users/" + username + UUID.randomUUID().toString().substring(0, 6);
    em.createNativeQuery(
            "INSERT INTO federation_remote_actor (actor_uri, key_id, public_key_pem, inbox,"
                + " shared_inbox, username, domain, fetched_at, created_at, updated_at)"
                + " VALUES (:uri, :key, 'pem', :inbox, NULL, :name, 'rc.example', NOW(6), NOW(6),"
                + " NOW(6))")
        .setParameter("uri", uri)
        .setParameter("key", uri + "#main-key")
        .setParameter("inbox", uri + "/inbox")
        .setParameter("name", username)
        .executeUpdate();
    return ((Number)
            em.createNativeQuery("SELECT id FROM federation_remote_actor WHERE actor_uri = :uri")
                .setParameter("uri", uri)
                .getSingleResult())
        .longValue();
  }

  private RemoteNotes.Received remoteReply(long actor, String uri, long parent) {
    return new RemoteNotes.Received(
        actor,
        uri,
        null,
        "먼 곳의 답글",
        null,
        false,
        "public",
        null,
        parent,
        "https://kurl.me/ap/notes/" + parent,
        List.of(),
        List.of(),
        null);
  }

  private String handle(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }

  private long user(String prefix) {
    return user(handle(prefix), true);
  }

  private long user(String handle, boolean claim) {
    UserEntity u = userRepository.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    if (claim) {
      u.claimUsername(handle);
    }
    return userRepository.save(u).getId();
  }
}
