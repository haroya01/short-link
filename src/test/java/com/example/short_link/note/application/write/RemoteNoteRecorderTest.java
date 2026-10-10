package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NoteRevisedEvent;
import com.example.short_link.common.note.RemoteNotes;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteVisibility;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RemoteNoteRecorderTest {

  private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");
  private static final String URI = "https://m.example/users/alice/statuses/9";

  @Mock private NoteRepository notes;
  @Mock private NoteMediaRepository media;
  @Mock private NotePeopleReader people;
  @Mock private ApplicationEventPublisher events;

  private RemoteNoteRecorder recorder() {
    return new RemoteNoteRecorder(notes, media, people, events, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static RemoteNotes.Received received(
      String visibility,
      Instant published,
      Long localParent,
      String parentUri,
      List<Long> addressed,
      List<RemoteNotes.Media> images,
      String warning) {
    return new RemoteNotes.Received(
        42L,
        URI,
        "https://m.example/@alice/9",
        "hello from afar",
        warning,
        false,
        visibility,
        published,
        localParent,
        parentUri,
        addressed,
        images,
        "en");
  }

  @Test
  void aPublicNoteIsStoredWithItsImagesAndNoOneIsTold() {
    when(notes.insertRemote(any())).thenReturn(Optional.of(900L));

    Optional<Long> id =
        recorder()
            .receive(
                received(
                    "public",
                    Instant.parse("2026-10-07T01:00:00.123456789Z"),
                    null,
                    null,
                    List.of(),
                    List.of(
                        new RemoteNotes.Media(
                            "https://m.example/1.png", " a cat ", "image/png", 1200, 900),
                        new RemoteNotes.Media("https://m.example/2", null, null)),
                    null));

    assertThat(id).contains(900L);
    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().visibility()).isEqualTo(NoteVisibility.PUBLIC);
    assertThat(row.getValue().language()).isEqualTo("en");
    assertThat(row.getValue().createdAt()).isEqualTo(Instant.parse("2026-10-07T01:00:00.123456Z"));
    assertThat(row.getValue().inReplyToId()).isNull();
    assertThat(row.getValue().reply()).isFalse();
    assertThat(row.getValue().sensitive()).isFalse();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<NoteMediaEntity>> images = ArgumentCaptor.forClass(List.class);
    verify(media).saveAll(images.capture());
    assertThat(images.getValue())
        .extracting(NoteMediaEntity::getAltText)
        .containsExactly("a cat", null);
    assertThat(images.getValue())
        .extracting(NoteMediaEntity::getContentType)
        .containsExactly("image/png", "image/jpeg");
    assertThat(images.getValue())
        .extracting(NoteMediaEntity::getWidth, NoteMediaEntity::getHeight)
        .containsExactly(tuple(1200, 900), tuple(null, null));
    verify(notes, never()).addRecipients(any(), anyCollection());
    verifyNoInteractions(events);
  }

  @Test
  void aReplyToAMembersNoteTellsThemAndNamedMembersHearOfAMention() {
    NoteEntity parent = new NoteEntity(7L, "my question", null, null);
    ReflectionTestUtils.setField(parent, "id", 5L);
    when(notes.findById(5L)).thenReturn(Optional.of(parent));
    when(notes.insertRemote(any())).thenReturn(Optional.of(901L));

    recorder().receive(received("direct", null, 5L, null, List.of(7L, 8L), List.of(), "cw"));

    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().inReplyToId()).isEqualTo(5L);
    assertThat(row.getValue().reply()).isTrue();
    assertThat(row.getValue().createdAt()).isEqualTo(NOW);
    assertThat(row.getValue().contentWarning()).isEqualTo("cw");
    assertThat(row.getValue().sensitive()).isTrue();
    verify(notes).addRecipients(901L, List.of(7L, 8L));
    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.REPLY,
                7L,
                null,
                42L,
                5L,
                "my question",
                901L,
                "hello from afar",
                5L));
    verify(events)
        .publishEvent(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.MENTION,
                8L,
                null,
                42L,
                901L,
                "hello from afar",
                null,
                null,
                5L));
  }

  @Test
  void aReplyInAThreadElsewhereFindsItsParentByUriAndAFutureDateIsClamped() {
    NoteEntity parent = new NoteEntity(null, "theirs", null, null);
    ReflectionTestUtils.setField(parent, "id", 6L);
    ReflectionTestUtils.setField(parent, "remoteActorId", 43L);
    when(notes.idByUri("https://m.example/s/1")).thenReturn(Optional.of(6L));
    when(notes.findById(6L)).thenReturn(Optional.of(parent));
    when(notes.insertRemote(any())).thenReturn(Optional.of(902L));

    recorder()
        .receive(
            received(
                "unlisted",
                NOW.plusSeconds(3600),
                null,
                "https://m.example/s/1",
                List.of(),
                List.of(),
                null));

    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().inReplyToId()).isEqualTo(6L);
    assertThat(row.getValue().createdAt()).isEqualTo(NOW);
    verifyNoInteractions(events);
  }

  @Test
  void aNoteDeliveredTwiceIsStoredOnceAndAnUnknownVisibilityIsDirect() {
    when(notes.insertRemote(any())).thenReturn(Optional.empty());

    assertThat(recorder().receive(received("odd", null, null, null, List.of(), List.of(), null)))
        .isEmpty();
    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().visibility()).isEqualTo(NoteVisibility.DIRECT);
    verifyNoInteractions(media, events);
  }

  private static NoteEntity kept(String body, Instant editedAt) {
    NoteEntity note = new NoteEntity(null, body, null, null);
    ReflectionTestUtils.setField(note, "id", 900L);
    ReflectionTestUtils.setField(note, "remoteActorId", 42L);
    ReflectionTestUtils.setField(note, "editedAt", editedAt);
    note.writeIn("en");
    return note;
  }

  private static NoteMediaEntity image(String url, String alt) {
    return new NoteMediaEntity(900L, 0, "", url, "image/png", alt, 1200, 900);
  }

  @Test
  void anUpdateRewritesTextWarningLanguageAndMediaAndTellsWhoSharedTheNote() {
    NoteEntity note = kept("hello #cats", null);
    when(notes.findRemoteForUpdate(42L, 900L)).thenReturn(Optional.of(note));
    when(media.findByNoteIds(List.of(900L)))
        .thenReturn(List.of(image("https://m.example/1.png", "a cat")));

    boolean revised =
        recorder()
            .revise(
                42L,
                900L,
                new RemoteNotes.Revision(
                    "edited #dogs",
                    "x".repeat(150),
                    false,
                    Instant.parse("2026-10-07T02:00:00.123456789Z"),
                    List.of(
                        new RemoteNotes.Media(
                            "https://m.example/1.png", "a cat", "image/png", 1200, 900),
                        new RemoteNotes.Media("https://m.example/2.png", "a dog", "image/png")),
                    "ja"));

    assertThat(revised).isTrue();
    assertThat(note.getBody()).isEqualTo("edited #dogs");
    assertThat(note.getContentWarning()).isEqualTo("x".repeat(99) + "…");
    assertThat(note.isSensitive()).isTrue();
    assertThat(note.getLanguage()).isEqualTo("ja");
    assertThat(note.getEditedAt()).isEqualTo(Instant.parse("2026-10-07T02:00:00.123456Z"));
    verify(notes).retag(900L, List.of("dogs"));
    verify(media).deleteAllByNoteId(900L);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<NoteMediaEntity>> images = ArgumentCaptor.forClass(List.class);
    verify(media).saveAll(images.capture());
    assertThat(images.getValue())
        .extracting(NoteMediaEntity::getUrl, NoteMediaEntity::getPosition)
        .containsExactly(tuple("https://m.example/1.png", 0), tuple("https://m.example/2.png", 1));
    verify(events).publishEvent(new NoteRevisedEvent(900L, null, 42L, "edited #dogs"));
  }

  @Test
  void anOlderUpdateOrTheSameContentAgainIsNoEditAndTellsNoOne() {
    Instant edited = Instant.parse("2026-10-07T02:00:00Z");
    NoteEntity note = kept("hello #cats", edited);
    when(notes.findRemoteForUpdate(42L, 900L)).thenReturn(Optional.of(note));
    when(media.findByNoteIds(List.of(900L)))
        .thenReturn(List.of(image("https://m.example/1.png", "a cat")));
    List<RemoteNotes.Media> sameImage =
        List.of(
            new RemoteNotes.Media("https://m.example/1.png", " a cat ", "image/png", 1200, 900));

    assertThat(
            recorder()
                .revise(
                    42L,
                    900L,
                    new RemoteNotes.Revision(
                        "an older text", null, false, edited.minusSeconds(60), sameImage, "ja")))
        .isTrue();
    assertThat(note.getBody()).isEqualTo("hello #cats");
    assertThat(note.getLanguage()).isEqualTo("en");

    assertThat(
            recorder()
                .revise(
                    42L,
                    900L,
                    new RemoteNotes.Revision(
                        "hello #cats", " ", false, edited.plusSeconds(60), sameImage, "ja")))
        .isTrue();
    assertThat(note.getEditedAt()).isEqualTo(edited);
    assertThat(note.getLanguage()).isEqualTo("ja");

    verify(notes, never()).retag(any(), any());
    verify(media, never()).deleteAllByNoteId(any());
    verify(media, never()).saveAll(any());
    verifyNoInteractions(events);
  }

  @Test
  void editsAndDeletesTouchOnlyTheSendersNote() {
    when(notes.findRemoteForUpdate(43L, 900L)).thenReturn(Optional.empty());
    when(notes.deleteRemote(42L, URI)).thenReturn(1, 0);
    when(notes.idByUri(URI)).thenReturn(Optional.of(900L));

    assertThat(
            recorder()
                .revise(
                    43L,
                    900L,
                    new RemoteNotes.Revision("edited", null, false, null, List.of(), null)))
        .isFalse();
    verifyNoInteractions(media, events);
    assertThat(recorder().retract(42L, URI)).isTrue();
    assertThat(recorder().retract(42L, URI)).isFalse();
    assertThat(recorder().exists(URI)).isTrue();
    assertThat(recorder().exists(null)).isFalse();
  }

  @Test
  void aNoteFromElsewhereIsFiledUnderItsTagsAndAReplyToANoteThatNeverArrivedStaysAReply() {
    when(notes.idByUri("https://m.example/s/404")).thenReturn(Optional.empty());
    when(notes.insertRemote(any())).thenReturn(Optional.of(903L));

    recorder()
        .receive(
            new RemoteNotes.Received(
                42L,
                URI,
                null,
                "answering #Cats and #dogs",
                null,
                false,
                "public",
                null,
                null,
                "https://m.example/s/404",
                List.of(),
                List.of(),
                null));

    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().inReplyToId()).isNull();
    assertThat(row.getValue().conversationId()).isNull();
    assertThat(row.getValue().reply()).isTrue();
    verify(notes).tag(903L, List.of("Cats", "dogs"));
  }

  @Test
  void anAnswerFromElsewhereToALimitedThreadStaysUnattachedUnlessItsSenderMayReply() {
    NoteEntity root = new NoteEntity(7L, "@bob@m.example 에게만", null, null);
    ReflectionTestUtils.setField(root, "id", 5L);
    root.limitReplies(NoteReplyPolicy.MENTIONED);
    when(notes.findById(5L)).thenReturn(Optional.of(root));
    when(notes.insertRemote(any())).thenReturn(Optional.of(910L), Optional.of(911L));
    when(people.remoteAuthors(java.util.Set.of(42L)))
        .thenReturn(
            java.util.Map.of(42L, NoteAuthor.remote(42L, "alice@m.example", null, null, null)),
            java.util.Map.of(42L, NoteAuthor.remote(42L, "Bob@m.example", null, null, null)));

    recorder().receive(received("public", null, 5L, null, List.of(7L), List.of(), null));
    recorder().receive(received("public", null, 5L, null, List.of(7L), List.of(), null));

    ArgumentCaptor<RemoteNoteRow> rows = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes, times(2)).insertRemote(rows.capture());
    assertThat(rows.getAllValues())
        .extracting(RemoteNoteRow::inReplyToId, RemoteNoteRow::replyPolicy)
        .containsExactly(
            tuple(null, NoteReplyPolicy.EVERYONE), tuple(5L, NoteReplyPolicy.MENTIONED));
    verify(events, times(1))
        .publishEvent(
            org.mockito.ArgumentMatchers.<NoteInteractionEvent>argThat(
                event -> event.type() == NoteInteractionEvent.Type.REPLY));
  }

  @Test
  void underFollowingTheThreadsWriterFollowingTheSenderLetsTheAnswerIn() {
    NoteEntity root = new NoteEntity(7L, "팔로우한 사람만", null, null);
    ReflectionTestUtils.setField(root, "id", 5L);
    root.limitReplies(NoteReplyPolicy.FOLLOWING);
    when(notes.findById(5L)).thenReturn(Optional.of(root));
    when(notes.insertRemote(any())).thenReturn(Optional.of(912L));
    when(people.remoteAuthors(java.util.Set.of(42L))).thenReturn(java.util.Map.of());
    when(people.followsRemote(7L, 42L)).thenReturn(true);

    recorder().receive(received("public", null, 5L, null, List.of(), List.of(), null));

    ArgumentCaptor<RemoteNoteRow> row = ArgumentCaptor.forClass(RemoteNoteRow.class);
    verify(notes).insertRemote(row.capture());
    assertThat(row.getValue().inReplyToId()).isEqualTo(5L);
  }

  @Test
  void onlyANoteFromElsewhereHasATarget() {
    NoteEntity remote = new NoteEntity(null, "theirs", null, null);
    ReflectionTestUtils.setField(remote, "id", 6L);
    ReflectionTestUtils.setField(remote, "remoteActorId", 43L);
    ReflectionTestUtils.setField(remote, "uri", URI);
    remote.showTo(NoteVisibility.PRIVATE);
    NoteEntity mine = new NoteEntity(7L, "mine", null, null);
    when(notes.findById(6L)).thenReturn(Optional.of(remote));
    when(notes.findById(5L)).thenReturn(Optional.of(mine));

    assertThat(recorder().target(6L)).contains(new RemoteNotes.Target(URI, 43L, false));
    assertThat(recorder().target(5L)).isEmpty();
  }
}
