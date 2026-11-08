package com.example.short_link.note.application.write;

import com.example.short_link.common.note.Mentions;
import com.example.short_link.common.note.NoteVisibility;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.ReplyGate;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.NoteRoomReader;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NoteDraftCheck {

  private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");

  private final NoteRepository notes;
  private final QuotedPostReader quotedPosts;
  private final NotePeopleReader people;
  private final NoteRoomReader rooms;
  private final UserModerationGuard moderation;
  private final NoteWriteGuard guard;

  record Checked(
      Text text,
      NoteEntity parent,
      Quote quote,
      List<String> handles,
      Map<Long, NoteAuthor> authors,
      String language,
      Audience audience) {}

  record Text(String body, List<String> pollOptions) {}

  record Quote(NoteEntity note, NoteAuthor author, QuotedPost post) {}

  record Audience(NoteVisibility requested, NoteReplyPolicy replyPolicy, Long roomId) {}

  Checked check(Long userId, NoteDraft draft) {
    moderation.requireCanWrite(userId);
    Text text = text(draft);
    NoteVisibility requested = visibility(draft.visibility());
    NoteEntity parent = parent(userId, draft.inReplyToId());
    requireSingleQuote(draft);
    NoteEntity quotedNote = quotedNote(userId, draft.quotedNoteId());
    QuotedPost quotedPost = quotedPost(draft.quotedPostId());
    List<String> handles = Mentions.of(text.body());
    Map<Long, NoteAuthor> authors = authors(userId, quotedNote, handles);
    NoteAuthor quotedAuthor = quotedAuthor(userId, quotedNote, draft.quotedNoteId(), authors);
    requireMayAnswer(userId, parent, authors.get(userId));
    Long roomId = room(userId, draft.roomId(), requested, parent);
    return new Checked(
        text,
        parent,
        new Quote(quotedNote, quotedAuthor, quotedPost),
        handles,
        authors,
        language(draft.language()),
        new Audience(requested, replyPolicy(draft.replyPolicy()), roomId));
  }

  private static Text text(NoteDraft draft) {
    boolean hasImages = draft.images() != null && !draft.images().isEmpty();
    String body = body(draft.body(), hasImages);
    List<String> pollOptions = draft.poll() == null ? null : pollOptions(draft.poll());
    if (pollOptions != null && hasImages) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_WITH_MEDIA);
    }
    return new Text(body, pollOptions);
  }

  private NoteEntity parent(Long userId, Long parentId) {
    if (parentId == null) {
      return null;
    }
    NoteEntity parent = guard.readable(userId, parentId);
    guard.requireNoBlockBetween(userId, parent, NoteErrorCode.NOTE_REPLY_BLOCKED);
    return parent;
  }

  private static void requireSingleQuote(NoteDraft draft) {
    if (draft.quotedPostId() != null && draft.quotedNoteId() != null) {
      throw new NoteException(NoteErrorCode.NOTE_QUOTE_CONFLICT);
    }
  }

  private NoteEntity quotedNote(Long userId, Long quotedNoteId) {
    if (quotedNoteId == null) {
      return null;
    }
    NoteEntity quoted =
        notes
            .findById(quotedNoteId)
            .orElseThrow(
                () -> new NoteException(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND, quotedNoteId));
    guard.requireReadable(userId, quoted);
    if (!quoted.getVisibility().shareable()) {
      throw new NoteException(NoteErrorCode.NOTE_NOT_SHAREABLE);
    }
    guard.requireNoBlockBetween(userId, quoted, NoteErrorCode.NOTE_INTERACTION_BLOCKED);
    return quoted;
  }

  private QuotedPost quotedPost(Long quotedPostId) {
    if (quotedPostId == null) {
      return null;
    }
    QuotedPost quoted = quotedPosts.publishedByIds(Set.of(quotedPostId)).get(quotedPostId);
    if (quoted == null) {
      throw new NoteException(NoteErrorCode.NOTE_QUOTE_NOT_FOUND, quotedPostId);
    }
    return quoted;
  }

  private Map<Long, NoteAuthor> authors(Long userId, NoteEntity quotedNote, List<String> handles) {
    Set<Long> authorIds = new HashSet<>(Set.of(userId));
    if (quotedNote != null && !quotedNote.isRemote()) {
      authorIds.add(quotedNote.getUserId());
    }
    return handles.isEmpty()
        ? people.activeAuthors(authorIds)
        : people.activeAuthors(authorIds, handles);
  }

  private NoteAuthor quotedAuthor(
      Long userId, NoteEntity quotedNote, Long quotedNoteId, Map<Long, NoteAuthor> authors) {
    if (quotedNote == null) {
      return null;
    }
    NoteAuthor author =
        quotedNote.isRemote()
            ? people
                .remoteAuthors(Set.of(quotedNote.getRemoteActorId()), userId)
                .get(quotedNote.getRemoteActorId())
            : authors.get(quotedNote.getUserId());
    if (author == null) {
      throw new NoteException(NoteErrorCode.NOTE_QUOTED_NOTE_NOT_FOUND, quotedNoteId);
    }
    return author;
  }

  private void requireMayAnswer(Long userId, NoteEntity parent, NoteAuthor replier) {
    if (parent == null || !parent.getReplyPolicy().limited()) {
      return;
    }
    NoteEntity root =
        parent.conversation().equals(parent.getId())
            ? parent
            : notes.findById(parent.conversation()).orElse(null);
    boolean admitted =
        ReplyGate.admits(
            root,
            new ReplyGate.Member(userId, replier == null ? null : replier.username()),
            () -> !people.followersOf(userId, Set.of(root.getUserId())).isEmpty());
    if (!admitted) {
      throw new NoteException(NoteErrorCode.NOTE_REPLY_RESTRICTED);
    }
  }

  private Long room(Long userId, Long asked, NoteVisibility requested, NoteEntity parent) {
    Long roomId = parent != null ? parent.getRoomId() : asked;
    if (asked != null && !asked.equals(roomId)) {
      throw new NoteException(NoteErrorCode.NOTE_ROOM_MISMATCH);
    }
    requireVisibilityFitsRoom(requested, roomId != null);
    if (roomId != null && !rooms.isMember(roomId, userId)) {
      throw new NoteException(NoteErrorCode.NOTE_ROOM_NOT_FOUND, roomId);
    }
    return roomId;
  }

  private static void requireVisibilityFitsRoom(NoteVisibility requested, boolean inRoom) {
    if (requested != null && requested.inRoom() != inRoom) {
      throw new NoteException(NoteErrorCode.NOTE_VISIBILITY_INVALID, requested.apiName());
    }
  }

  static String body(String raw, boolean hasImages) {
    String body = raw == null ? "" : raw.strip();
    if (body.isEmpty() && !hasImages) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_REQUIRED);
    }
    if (body.codePointCount(0, body.length()) > NoteEntity.MAX_BODY_LENGTH) {
      throw new NoteException(NoteErrorCode.NOTE_BODY_TOO_LONG, NoteEntity.MAX_BODY_LENGTH);
    }
    return body;
  }

  static String language(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String code = raw.strip().toLowerCase(Locale.ROOT);
    if (!LANGUAGE.matcher(code).matches()) {
      throw new NoteException(NoteErrorCode.NOTE_LANGUAGE_INVALID, raw);
    }
    return code;
  }

  private static NoteVisibility visibility(String raw) {
    return raw == null
        ? null
        : NoteVisibility.parse(raw)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_VISIBILITY_INVALID, raw));
  }

  private static NoteReplyPolicy replyPolicy(String raw) {
    return raw == null
        ? null
        : NoteReplyPolicy.parse(raw)
            .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_REPLY_POLICY_INVALID, raw));
  }

  private static List<String> pollOptions(NoteDraft.Poll poll) {
    List<String> options =
        (poll.options() == null ? List.<String>of() : poll.options())
            .stream()
                .map(option -> option == null ? "" : option.strip().replaceAll("\\s+", " "))
                .toList();
    if (!fits(options) || !lastsAllowedTime(poll.expiresIn())) {
      throw new NoteException(NoteErrorCode.NOTE_POLL_INVALID);
    }
    return options;
  }

  private static boolean fits(List<String> options) {
    return options.size() >= NoteEntity.MIN_POLL_OPTIONS
        && options.size() <= NoteEntity.MAX_POLL_OPTIONS
        && options.stream().allMatch(NoteDraftCheck::fitsOption)
        && new HashSet<>(options).size() == options.size();
  }

  private static boolean fitsOption(String option) {
    return !option.isEmpty()
        && option.codePointCount(0, option.length()) <= NoteEntity.MAX_POLL_OPTION_LENGTH;
  }

  private static boolean lastsAllowedTime(Long expiresIn) {
    return expiresIn != null
        && expiresIn >= NoteEntity.MIN_POLL_SECONDS
        && expiresIn <= NoteEntity.MAX_POLL_SECONDS;
  }
}
