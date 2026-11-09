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
      List<String> imageKeys) {

    boolean reachesElsewhere() {
      return (parent != null && parent.isRemote())
          || (quotedNote != null && quotedNote.isRemote())
          || !Mentions.remote(note.getBody()).isEmpty();
    }

    boolean answerSeen() {
      return parent != null && (readers == null || readers.contains(parent.getUserId()));
    }

    boolean broadcast() {
      return note.getVisibility().followersRead()
          && (parent == null || writerId.equals(parent.getUserId()));
    }

    boolean quotesPostPublicly() {
      return quotedPost != null && note.getVisibility().shareable();
    }

    Set<Long> toldOtherwise() {
      Set<Long> told = new HashSet<>(Set.of(writerId));
      if (parent != null) {
        told.add(parent.getUserId());
      }
      if (quoteSeen) {
        told.add(quotedNote.getUserId());
      }
      if (quotesPostPublicly()) {
        told.add(quotedPost.authorId());
      }
      return told;
    }

    String previewUrl() {
      return NoteLinks.previewUrl(
          note.getBody(),
          !imageKeys.isEmpty() || note.hasPoll(),
          quotedPost != null || quotedNote != null);
    }
  }

  static List<Object> events(Posted posted) {
    NoteEntity note = posted.note();
    Long writerId = posted.writerId();
    List<Object> told = new ArrayList<>();
    if (!posted.imageKeys().isEmpty()) {
      told.add(new ImageVariantsRequested(posted.imageKeys()));
    }
    told.add(new NotePublishedEvent(note.getId(), writerId, posted.reachesElsewhere()));
    if (posted.answerSeen()) {
      told.add(
          NoteInteractions.interaction(
              NoteInteractionEvent.Type.REPLY, posted.parent(), writerId, note));
    }
    if (posted.broadcast()) {
      told.add(new NoteBroadcastEvent(note.getId(), writerId, note.excerpt()));
    }
    if (posted.quoteSeen()) {
      told.add(
          NoteInteractions.interaction(
              NoteInteractionEvent.Type.QUOTE, posted.quotedNote(), writerId, note));
    }
    if (posted.quotesPostPublicly()) {
      told.add(
          new PostQuotedEvent(
              posted.quotedPost().authorId(), writerId, note.getId(), note.excerpt()));
    }
    told.addAll(
        mentions(
            note,
            writerId,
            readersOf(posted.mentioned(), posted.readers()),
            posted.toldOtherwise()));
    String previewUrl = posted.previewUrl();
    if (previewUrl != null) {
      told.add(new NoteLinkPreviewRequested(note.getId(), previewUrl));
    }
    NoteLiveChanges.of(
            note.getId(), note.getInReplyToId(), note.getRoomId(), note.getVisibility().direct())
        .ifPresent(told::add);
    return told;
  }

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
                note.conversation(),
                note.getRoomId()));
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
