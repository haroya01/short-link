package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileRepliesAndMediaIntegrationTest {

  @Autowired private NoteQueryService query;
  @Autowired private NoteRepository notes;
  @Autowired private NoteMediaRepository media;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private BlockRepository blocks;
  @Autowired private MuteRepository mutes;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager em;

  private long author;
  private long viewer;
  private long friend;
  private long muted;
  private long blocked;

  @BeforeEach
  void membersAndTheViewersSilences() {
    author = member("pr_author");
    viewer = member("pr_viewer");
    friend = member("pr_friend");
    muted = member("pr_muted");
    blocked = member("pr_blocked");
    mutes.save(new UserMuteEntity(viewer, muted, false, null));
    blocks.save(new UserBlockEntity(viewer, blocked));
  }

  @Test
  void repliesAreTheAuthorsAnswersToOthersNewestFirstWithWhatEachAnswered() {
    long friendNote = note(friend, "a thought worth answering", null);
    long ownRoot = note(author, "my own thread starts here", null);
    note(author, "and goes on here", ownRoot);
    note(author, "a top-level note", null);
    long toFriend = note(author, "I agree", friendNote);
    long warned =
        note(friend, "the ending of the film", null, n -> n.markContent("spoiler", false));
    long toWarned = note(author, "no way", warned);
    long gone = note(friend, "soon deleted", null);
    long orphan = note(author, "answering something gone", gone);
    em.flush();
    jdbc.update("DELETE FROM note WHERE id = ?", gone);
    em.clear();

    List<ProfileRepliesView.Item> items = replies(viewer).items();

    assertThat(items)
        .extracting(item -> item.note().id())
        .containsExactly(orphan, toWarned, toFriend);
    assertThat(items.get(0).replyingTo()).isNull();
    ProfileRepliesView.ReplyContext spoiler = items.get(1).replyingTo();
    assertThat(spoiler.id()).isEqualTo(warned);
    assertThat(spoiler.excerpt()).isNull();
    assertThat(spoiler.contentWarning()).isEqualTo("spoiler");
    ProfileRepliesView.ReplyContext answered = items.get(2).replyingTo();
    assertThat(answered.id()).isEqualTo(friendNote);
    assertThat(answered.author().username()).isEqualTo("pr_friend");
    assertThat(answered.excerpt()).isEqualTo("a thought worth answering");
    assertThat(answered.contentWarning()).isNull();
  }

  @Test
  void aParentTheViewerCannotHearOrReadIsLeftBlankWhileTheReplyStays() {
    long toMuted = note(author, "to the muted", note(muted, "muted voice", null));
    long toBlocked = note(author, "to the blocked", note(blocked, "blocked voice", null));
    long toPrivate =
        note(
            author,
            "to a followers-only note",
            note(friend, "for followers", null, n -> n.showTo(NoteVisibility.PRIVATE)));

    List<ProfileRepliesView.Item> seen = replies(viewer).items();
    assertThat(seen)
        .extracting(item -> item.note().id())
        .containsExactly(toPrivate, toBlocked, toMuted);
    assertThat(seen).allSatisfy(item -> assertThat(item.replyingTo()).isNull());

    List<ProfileRepliesView.Item> anonymous = replies(null).items();
    assertThat(anonymous.get(0).replyingTo()).isNull();
    assertThat(anonymous.get(1).replyingTo().excerpt()).isEqualTo("blocked voice");
    assertThat(anonymous.get(2).replyingTo().excerpt()).isEqualTo("muted voice");

    follows.save(new FollowEntity(viewer, friend));
    assertThat(replies(viewer).items().get(0).replyingTo().excerpt()).isEqualTo("for followers");
  }

  @Test
  void repliesFollowTheProfileRulesForVisibilityAndBlocks() {
    long parent = note(friend, "open question", null);
    long open = note(author, "an open answer", parent);
    long forFollowers =
        note(author, "for my followers", parent, n -> n.showTo(NoteVisibility.PRIVATE));
    note(author, "only for you", parent, n -> n.showTo(NoteVisibility.DIRECT));

    assertThat(ids(replies(viewer))).containsExactly(open);
    assertThat(ids(replies(author))).hasSize(3);
    follows.save(new FollowEntity(viewer, author));
    assertThat(ids(replies(viewer))).containsExactly(forFollowers, open);

    mutes.save(new UserMuteEntity(viewer, author, false, null));
    assertThat(ids(replies(viewer))).containsExactly(forFollowers, open);

    blocks.save(new UserBlockEntity(author, viewer));
    assertThat(replies(viewer).items()).isEmpty();
    assertThat(ids(replies(null))).containsExactly(open);
  }

  @Test
  void mediaIsEveryNoteWithAttachmentsNewestFirstWithItsFirstOneAndCount() {
    note(author, "words only", null);
    long two = note(author, "two photos", null);
    attach(two, "first", "second");
    long reply = note(author, "a photo in reply", note(friend, "show me", null));
    attach(reply, "answer");
    long warned = note(author, "careful", null, n -> n.markContent("flash", false));
    attach(warned, "bright");
    long quiet = note(author, "marked sensitive", null, n -> n.markContent(null, true));
    attach(quiet, "quiet");

    List<ProfileMediaView.Item> items = query.mediaByAuthor("pr_author", 0, 20, viewer).items();

    assertThat(items)
        .extracting(ProfileMediaView.Item::noteId)
        .containsExactly(quiet, warned, reply, two);
    ProfileMediaView.Item photos = items.get(3);
    assertThat(photos.media().altText()).isEqualTo("first");
    assertThat(photos.mediaCount()).isEqualTo(2);
    assertThat(photos.sensitive()).isFalse();
    assertThat(items.get(1).sensitive()).isTrue();
    assertThat(items.get(1).contentWarning()).isEqualTo("flash");
    assertThat(items.get(0).sensitive()).isTrue();
    assertThat(items.get(0).contentWarning()).isNull();
  }

  @Test
  void mediaFollowsTheProfileRulesForVisibilityAndBlocks() {
    long open = note(author, "open photo", null);
    attach(open, "open");
    long forFollowers =
        note(author, "followers photo", null, n -> n.showTo(NoteVisibility.PRIVATE));
    attach(forFollowers, "followers");

    assertThat(mediaIds(viewer)).containsExactly(open);
    follows.save(new FollowEntity(viewer, author));
    assertThat(mediaIds(viewer)).containsExactly(forFollowers, open);
    blocks.save(new UserBlockEntity(viewer, author));
    assertThat(mediaIds(viewer)).isEmpty();
    assertThat(mediaIds(null)).containsExactly(open);
  }

  @Test
  void bothTabsPage() {
    long parent = note(friend, "q", null);
    long older = note(author, "first answer", parent);
    long newer = note(author, "second answer", parent);
    attach(older, "a");
    attach(newer, "b");
    note(author, "newest, with words only", null);

    ProfileRepliesView first = query.repliesByAuthor("pr_author", 0, 1, viewer);
    ProfileRepliesView second = query.repliesByAuthor("pr_author", 1, 1, viewer);
    assertThat(ids(first)).containsExactly(newer);
    assertThat(first.hasNext()).isTrue();
    assertThat(ids(second)).containsExactly(older);
    assertThat(second.hasNext()).isFalse();

    ProfileMediaView firstMedia = query.mediaByAuthor("pr_author", 0, 1, viewer);
    assertThat(firstMedia.items()).extracting(ProfileMediaView.Item::noteId).containsExactly(newer);
    assertThat(firstMedia.hasNext()).isTrue();
  }

  private ProfileRepliesView replies(Long reader) {
    return query.repliesByAuthor("pr_author", 0, 20, reader);
  }

  private static List<Long> ids(ProfileRepliesView view) {
    return view.items().stream().map(item -> item.note().id()).toList();
  }

  private List<Long> mediaIds(Long reader) {
    return query.mediaByAuthor("pr_author", 0, 20, reader).items().stream()
        .map(ProfileMediaView.Item::noteId)
        .toList();
  }

  private long member(String handle) {
    UserEntity user = users.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    user.claimUsername(handle);
    return users.save(user).getId();
  }

  private long note(long writer, String body, Long inReplyTo) {
    return note(writer, body, inReplyTo, n -> {});
  }

  private long note(long writer, String body, Long inReplyTo, Consumer<NoteEntity> shape) {
    NoteEntity note = new NoteEntity(writer, body, inReplyTo, null);
    shape.accept(note);
    return notes.save(note).getId();
  }

  private void attach(long noteId, String... alts) {
    List<NoteMediaEntity> attached = new ArrayList<>();
    for (int i = 0; i < alts.length; i++) {
      attached.add(
          new NoteMediaEntity(
              noteId,
              i,
              "notes/" + noteId + "/" + i,
              "https://cdn.example/notes/" + noteId + "/" + i + ".webp",
              "image/webp",
              alts[i]));
    }
    media.saveAll(attached);
  }
}
