package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.PostQuotedEvent;
import com.example.short_link.common.image.ImageVariantsRequested;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.QuotedPost;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotePublicationTest {

  private static final NoteAuthor MINA = new NoteAuthor(9L, "mina", null);
  private static final NoteAuthor BOB = new NoteAuthor(8L, "bob", null);

  private static NoteEntity note(Long id, Long userId, String body, NoteEntity parent) {
    NoteEntity note = new NoteEntity(userId, body, parent == null ? null : parent.getId(), null);
    ReflectionTestUtils.setField(note, "id", id);
    if (parent != null) {
      note.answer(parent);
    }
    return note;
  }

  private static List<Object> told(
      NoteEntity note,
      NoteEntity parent,
      NoteEntity quotedNote,
      QuotedPost quotedPost,
      List<NoteAuthor> mentioned,
      Set<Long> readers,
      boolean quoteSeen,
      List<String> imageKeys) {
    return NotePublication.events(
        new NotePublication.Posted(
            note, 7L, parent, quotedNote, quotedPost, mentioned, readers, quoteSeen, imageKeys));
  }

  private static List<Class<?>> kinds(List<Object> events) {
    return events.stream().<Class<?>>map(Object::getClass).toList();
  }

  @Test
  void aPublicTopLevelNoteGoesOutToFollowersAndAsksForItsCard() {
    NoteEntity note = note(100L, 7L, "봐요 https://example.com/a", null);

    List<Object> events = told(note, null, null, null, List.of(), null, false, List.of());

    assertThat(events)
        .containsExactly(
            new NotePublishedEvent(100L, 7L, false),
            new NoteBroadcastEvent(100L, 7L, "봐요 https://example.com/a"),
            new NoteLinkPreviewRequested(100L, "https://example.com/a"));
  }

  @Test
  void imagesComeFirstAndANoteWithThemOrAQuoteAsksForNoCard() {
    NoteEntity note = note(100L, 7L, "사진 https://example.com/a", null);

    assertThat(kinds(told(note, null, null, null, List.of(), null, false, List.of("k1", "k2"))))
        .containsExactly(
            ImageVariantsRequested.class, NotePublishedEvent.class, NoteBroadcastEvent.class);
    assertThat(
            told(
                note,
                null,
                null,
                new QuotedPost(5L, "Essay", "essay", "writer", 3L),
                List.of(),
                null,
                false,
                List.of()))
        .noneMatch(NoteLinkPreviewRequested.class::isInstance);
  }

  @Test
  void aReplyToSomeoneElseTellsThemButNotTheFollowers() {
    NoteEntity parent = note(50L, 9L, "질문", null);
    NoteEntity reply = note(100L, 7L, "답", parent);

    List<Object> events = told(reply, parent, null, null, List.of(), null, false, List.of());

    assertThat(kinds(events)).containsExactly(NotePublishedEvent.class, NoteInteractionEvent.class);
    assertThat(((NoteInteractionEvent) events.get(1)).type())
        .isEqualTo(NoteInteractionEvent.Type.REPLY);
    assertThat(((NoteInteractionEvent) events.get(1)).recipientUserId()).isEqualTo(9L);
  }

  @Test
  void aReplyInOnesOwnThreadGoesOutToFollowersAndTellsNoOne() {
    NoteEntity parent = note(50L, 7L, "처음", null);
    NoteEntity reply = note(100L, 7L, "이어서", parent);

    List<Object> events = told(reply, parent, null, null, List.of(), null, false, List.of());

    assertThat(events)
        .filteredOn(NoteInteractionEvent.class::isInstance)
        .extracting(event -> ((NoteInteractionEvent) event).isSelfAction())
        .containsOnly(true);
    assertThat(events).anyMatch(NoteBroadcastEvent.class::isInstance);
  }

  @Test
  void aNoteThatNarrowsItsReadersTellsOnlyTheNamedWhoMayReadIt() {
    NoteEntity parent = note(50L, 9L, "방 질문", null);
    NoteEntity inRoom = note(100L, 7L, "@mina @bob 방에서", parent);
    inRoom.showTo(NoteVisibility.ROOM);

    List<Object> events =
        told(inRoom, parent, null, null, List.of(MINA, BOB), Set.of(8L), false, List.of());

    assertThat(events)
        .filteredOn(NoteInteractionEvent.class::isInstance)
        .extracting(event -> ((NoteInteractionEvent) event).type())
        .containsExactly(NoteInteractionEvent.Type.MENTION);
    assertThat(events).noneMatch(NoteBroadcastEvent.class::isInstance);
  }

  @Test
  void whoeverHearsAsTheRepliedToQuotedOrQuotedPostsWriterIsNotToldAgainForAMention() {
    NoteEntity parent = note(50L, 9L, "질문", null);
    NoteEntity quoted = note(60L, 8L, "인용될 노트", null);
    NoteEntity note = note(100L, 7L, "@mina @bob 답과 인용", parent);
    ReflectionTestUtils.setField(note, "quotedNoteId", 60L);

    List<Object> events =
        told(note, parent, quoted, null, List.of(MINA, BOB), null, true, List.of());

    assertThat(events)
        .filteredOn(NoteInteractionEvent.class::isInstance)
        .extracting(event -> ((NoteInteractionEvent) event).type())
        .containsExactly(NoteInteractionEvent.Type.REPLY, NoteInteractionEvent.Type.QUOTE);
  }

  @Test
  void aQuotedPostsWriterHearsOnlyOfANoteEveryoneMayShare() {
    QuotedPost post = new QuotedPost(5L, "Essay", "essay", "writer", 3L);
    NoteEntity shared = note(100L, 7L, "인용", null);
    NoteEntity onlyUs = note(101L, 7L, "우리끼리", null);
    onlyUs.showTo(NoteVisibility.PRIVATE);

    assertThat(told(shared, null, null, post, List.of(), null, false, List.of()))
        .contains(new PostQuotedEvent(3L, 7L, 100L, "인용"));
    assertThat(told(onlyUs, null, null, post, List.of(), null, false, List.of()))
        .noneMatch(PostQuotedEvent.class::isInstance);
  }

  @Test
  void aNoteThatAnswersQuotesOrNamesSomeoneElsewhereReachesThatServer() {
    NoteEntity elsewhere = note(50L, null, "다른 서버", null);
    ReflectionTestUtils.setField(elsewhere, "remoteActorId", 42L);

    assertThat(
            told(
                note(100L, 7L, "@alice@m.example 안녕", null),
                null,
                null,
                null,
                List.of(),
                null,
                false,
                List.of()))
        .contains(new NotePublishedEvent(100L, 7L, true));
    assertThat(
            told(
                note(101L, 7L, "답", elsewhere),
                elsewhere,
                null,
                null,
                List.of(),
                null,
                false,
                List.of()))
        .contains(new NotePublishedEvent(101L, 7L, true));
  }
}
