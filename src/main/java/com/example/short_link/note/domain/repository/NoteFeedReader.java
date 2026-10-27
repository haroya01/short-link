package com.example.short_link.note.domain.repository;

import com.example.short_link.common.seen.Unseen;
import com.example.short_link.note.domain.LocalPoolRow;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.RemotePoolRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NoteFeedReader {

  List<NoteEntity> topLevel(Long viewerId, int offset, int limit, Unseen unseen);

  List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit);

  List<NoteEntity> repliesByAuthor(Long authorId, Long viewerId, int offset, int limit);

  List<NoteEntity> withMediaByAuthor(Long authorId, Long viewerId, int offset, int limit);

  List<NoteEntity> inRoom(Long roomId, Long viewerId, int offset, int limit);

  List<NoteEntity> inRoomsOf(Long viewerId, int offset, int limit);

  List<NoteEntity> roomNotesBy(Long authorId, int offset, int limit);

  List<NoteEntity> direct(Long viewerId, int offset, int limit);

  List<NoteEntity> federated(Long viewerId, int offset, int limit);

  List<NoteEntity> federatedBefore(Long viewerId, Long beforeId, int limit);

  // The notes a relay brought or a pull kept, newest first, as the federated feed filters them; a
  // note kept because it trended elsewhere is not among them.
  List<NoteEntity> latestBefore(Long viewerId, Long beforeId, int limit);

  // The same notes kept between since and until, newest first, without a warning or a sensitive
  // mark, and not by an account the viewer follows unless followed ones are asked for.
  List<RemotePoolRow> remotePool(
      Long viewerId, Instant since, Instant until, int limit, boolean withFollowed);

  // Members' public top-level notes written between since and until, newest first, heard and in
  // the viewer's languages as the home's latest has them, without room notes.
  List<LocalPoolRow> localPool(Long viewerId, Instant since, Instant until, int limit);

  List<NoteFeedRow> following(Collection<Long> authorIds, Long viewerId, int offset, int limit);

  List<NoteEntity> replies(Long noteId, Long viewerId, int limit);

  List<NoteEntity> hiddenReplies(Long noteId, Long viewerId, int limit);

  List<NoteEntity> byRemoteActor(Long remoteActorId, Long viewerId, int offset, int limit);

  List<NoteEntity> quotesOf(Long noteId, Long viewerId, int offset, int limit);

  List<NoteEntity> quotesOfPost(Long postId, Long viewerId, int offset, int limit);

  long countQuotesOfPost(Long postId, Long viewerId);

  List<NoteEntity> tagged(String tag, Long viewerId, int offset, int limit);

  List<NoteEntity> linked(String url, Long viewerId, int offset, int limit);

  List<NoteEntity> search(String match, String like, Long viewerId, int offset, int limit);
}
