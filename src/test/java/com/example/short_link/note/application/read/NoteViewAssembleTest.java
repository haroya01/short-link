package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteViewerMarks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoteViewAssembleTest {

  private static final NoteAuthor ME = new NoteAuthor(7L, "me", null);
  private static final NoteAuthor ALICE =
      NoteAuthor.remote(42L, "alice@m.example", null, "Alice", "https://m.example/@alice");

  private static NoteEntity note(Long id, Long userId) {
    NoteEntity note = new NoteEntity(userId, "n" + id, null, null);
    ReflectionTestUtils.setField(note, "id", id);
    return note;
  }

  private static NoteEntity remote(Long id, String url) {
    NoteEntity note = note(id, null);
    ReflectionTestUtils.setField(note, "remoteActorId", 42L);
    ReflectionTestUtils.setField(note, "uri", "https://m.example/notes/" + id);
    ReflectionTestUtils.setField(note, "remoteUrl", url);
    return note;
  }

  private static NoteBatch read(List<NoteEntity> visible, NoteViewerMarks marks) {
    return new NoteBatch(
        visible,
        Map.of(7L, ME),
        Map.of(42L, ALICE),
        Set.of("me"),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(1L, new NoteStats(1, 2, 3, 4)),
        marks,
        Map.of(),
        Map.of(),
        Set.of(),
        Map.of(30L, "책 모임"));
  }

  @Test
  void aSignedInReaderSeesTheirMarksAndAnAnonymousOneSeesNone() {
    NoteEntity mine = note(1L, 7L);
    NoteBatch batch =
        read(List.of(mine), new NoteViewerMarks(Set.of(1L), Set.of(), Set.of(1L), Set.of(1L)));

    NoteView signedIn = NoteViews.assemble(mine, batch, 7L);
    NoteView anonymous = NoteViews.assemble(mine, batch, null);

    assertThat(signedIn.likedByMe()).isTrue();
    assertThat(signedIn.repostedByMe()).isFalse();
    assertThat(signedIn.bookmarkedByMe()).isTrue();
    assertThat(signedIn.conversationMuted()).isTrue();
    assertThat(signedIn.canReply()).isTrue();
    assertThat(signedIn.likeCount()).isEqualTo(2);
    assertThat(signedIn.replyCount()).isEqualTo(1);
    assertThat(anonymous.likedByMe()).isNull();
    assertThat(anonymous.repostedByMe()).isNull();
    assertThat(anonymous.bookmarkedByMe()).isNull();
    assertThat(anonymous.conversationMuted()).isNull();
    assertThat(anonymous.canReply()).isNull();
    assertThat(anonymous.likeCount()).isEqualTo(2);
  }

  @Test
  void aNoteJustWrittenCountsNothingIsNotMutedAndTakesReplies() {
    NoteEntity fresh = note(100L, 7L);

    NoteView view =
        NoteViews.assemble(
            fresh,
            NoteBatch.justWritten(
                fresh, Map.of(7L, ME), null, null, null, Map.of(), null, Map.of()),
            7L);

    assertThat(view.author()).isEqualTo(ME);
    assertThat(view.likeCount()).isZero();
    assertThat(view.likedByMe()).isFalse();
    assertThat(view.conversationMuted()).isFalse();
    assertThat(view.canReply()).isTrue();
    assertThat(view.quotedNote()).isNull();
    assertThat(view.media()).isEmpty();
  }

  @Test
  void aNoteFromElsewhereLinksToItsPageOrElseToItsId() {
    NoteEntity web = remote(2L, "https://m.example/@alice/2");
    NoteEntity gopher = remote(3L, "gopher://m.example/3");
    NoteBatch batch = read(List.of(web, gopher), NoteViewerMarks.NONE);

    assertThat(NoteViews.assemble(web, batch, 7L).remote().url())
        .isEqualTo("https://m.example/@alice/2");
    assertThat(NoteViews.assemble(gopher, batch, 7L).remote().url())
        .isEqualTo("https://m.example/notes/3");
    assertThat(NoteViews.assemble(gopher, batch, 7L).author()).isEqualTo(ALICE);
  }

  @Test
  void aRoomNoteCarriesItsRoomsName() {
    NoteEntity inRoom = note(5L, 7L);
    inRoom.postIn(30L);
    inRoom.showTo(NoteVisibility.ROOM);

    NoteView view = NoteViews.assemble(inRoom, read(List.of(inRoom), NoteViewerMarks.NONE), 7L);

    assertThat(view.room()).isEqualTo(new NoteView.Room(30L, "책 모임"));
    assertThat(NoteViews.assemble(note(1L, 7L), read(List.of(), NoteViewerMarks.NONE), 7L).room())
        .isNull();
  }
}
