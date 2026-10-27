package com.example.short_link.note.application.write;

import com.example.short_link.common.event.Actor;
import com.example.short_link.common.event.NoteBroadcastEvent;
import com.example.short_link.common.event.NoteInteractionEvent;
import com.example.short_link.common.event.NotePublishedEvent;
import com.example.short_link.common.event.PostQuotedEvent;
import com.example.short_link.common.image.ImageVariantsRequested;
import com.example.short_link.common.note.Mentions;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteLinks;
import com.example.short_link.note.domain.QuotedPost;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Every listener of these events runs after the commit, so they are told in this order once the
// note is stored.
final class NotePublication {

  private NotePublication() {}

  // readers is null when everyone the note names may read it.
  record Posted(
      NoteEntity note,
      Long writerId,
      NoteEntity parent,
      NoteEntity quotedNote,
      QuotedPost quotedPost,
      List<NoteAuthor> mentioned,
      Set<Long> readers,
      boolean quoteSeen,
      List<String> imageKeys) {}

  static List<Object> events(Posted posted) {
    NoteEntity note = posted.note();
    NoteEntity parent = posted.parent();
    NoteEntity quotedNote = posted.quotedNote();
    QuotedPost quotedPost = posted.quotedPost();
    Long writerId = posted.writerId();
    Set<Long> readers = posted.readers();
    List<Object> told = new ArrayList<>();
    if (!posted.imageKeys().isEmpty()) {
      told.add(new ImageVariantsRequested(posted.imageKeys()));
    }
    told.add(
        new NotePublishedEvent(
            note.getId(),
            writerId,
            (parent != null && parent.isRemote())
                || (quotedNote != null && quotedNote.isRemote())
                || !Mentions.remote(note.getBody()).isEmpty()));
    if (parent != null && (readers == null || readers.contains(parent.getUserId()))) {
      told.add(
          NoteInteractions.interaction(NoteInteractionEvent.Type.REPLY, parent, writerId, note));
    }
    if (note.getVisibility().followersRead()
        && (parent == null || writerId.equals(parent.getUserId()))) {
      told.add(new NoteBroadcastEvent(note.getId(), writerId, note.excerpt()));
    }
    if (posted.quoteSeen()) {
      told.add(
          NoteInteractions.interaction(
              NoteInteractionEvent.Type.QUOTE, quotedNote, writerId, note));
    }
    boolean quotesPostPublicly = quotedPost != null && note.getVisibility().shareable();
    if (quotesPostPublicly) {
      told.add(new PostQuotedEvent(quotedPost.authorId(), writerId, note.getId(), note.excerpt()));
    }
    Set<Long> toldOtherwise = new HashSet<>(Set.of(writerId));
    if (parent != null) {
      toldOtherwise.add(parent.getUserId());
    }
    if (posted.quoteSeen()) {
      toldOtherwise.add(quotedNote.getUserId());
    }
    if (quotesPostPublicly) {
      toldOtherwise.add(quotedPost.authorId());
    }
    told.addAll(mentions(note, writerId, readersOf(posted.mentioned(), readers), toldOtherwise));
    String previewUrl =
        NoteLinks.previewUrl(
            note.getBody(),
            !posted.imageKeys().isEmpty() || note.hasPoll(),
            quotedPost != null || quotedNote != null);
    if (previewUrl != null) {
      told.add(new NoteLinkPreviewRequested(note.getId(), previewUrl));
    }
    return told;
  }

  // Whoever already hears about this note as the replied-to or quoted author is not told twice.
  static List<NoteInteractionEvent> mentions(
      NoteEntity note, Long actorId, List<NoteAuthor> mentioned, Set<Long> toldOtherwise) {
    List<NoteInteractionEvent> told = new ArrayList<>();
    for (NoteAuthor member : mentioned) {
      if (!toldOtherwise.contains(member.id())) {
        told.add(
            new NoteInteractionEvent(
                NoteInteractionEvent.Type.MENTION,
                member.id(),
                Actor.member(actorId),
                note.getId(),
                note.excerpt(),
                null,
                null,
                note.conversation()));
      }
    }
    return told;
  }

  static List<NoteAuthor> readersOf(List<NoteAuthor> named, Set<Long> readers) {
    return readers == null
        ? named
        : named.stream().filter(member -> readers.contains(member.id())).toList();
  }
}
