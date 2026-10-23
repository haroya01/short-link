package com.example.short_link.note.domain.repository;

import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.common.seen.Unseen;
import com.example.short_link.note.domain.LocalPoolRow;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.RemoteNoteRow;
import com.example.short_link.note.domain.RemotePoolRow;
import com.example.short_link.note.domain.RoomErasure;
import com.example.short_link.note.domain.SelfReply;
import com.example.short_link.note.domain.TrendingLink;
import com.example.short_link.note.domain.TrendingTag;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  // Empty also when the viewer blocked the note's author or the author blocked the viewer.
  Optional<NoteEntity> findUnblocked(Long id, Long viewerId);

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  List<NoteEntity> topLevel(Long viewerId, int offset, int limit, Unseen unseen);

  List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit);

  List<NoteEntity> repliesByAuthor(Long authorId, Long viewerId, int offset, int limit);

  List<NoteEntity> withMediaByAuthor(Long authorId, Long viewerId, int offset, int limit);

  // The given notes whose author, and server, the viewer can hear.
  List<NoteEntity> heardAmong(Collection<Long> ids, Long viewerId);

  Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds);

  Set<Long> readersAmong(Long noteId, Collection<Long> userIds);

  List<NoteEntity> inRoom(Long roomId, Long viewerId, int offset, int limit);

  List<NoteEntity> inRoomsOf(Long viewerId, int offset, int limit);

  // The newest replies to each note, at most perNote each, oldest first, in one statement: only
  // replies the viewer may read and hears, and not hidden by the note's writer.
  List<ReplyPreview> latestReplies(Collection<Long> noteIds, Long viewerId, int perNote);

  List<NoteEntity> roomNotesBy(Long authorId, int offset, int limit);

  List<Long> roomNoteIdsBy(Long authorId, Long roomId, int limit);

  void requestRoomErasure(Long userId, Long roomId, Instant at);

  List<RoomErasure> pendingRoomErasures(int limit);

  void finishRoomErasure(Long userId, Long roomId);

  void addRecipients(Long noteId, Collection<Long> userIds);

  List<NoteEntity> direct(Long viewerId, int offset, int limit);

  List<NoteEntity> trending(Long viewerId, int offset, int limit);

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

  Map<Long, List<String>> tagsOf(Collection<Long> noteIds);

  List<NoteFeedRow> following(Collection<Long> authorIds, Long viewerId, int offset, int limit);

  List<NoteEntity> replies(Long noteId, Long viewerId, int limit);

  List<NoteEntity> hiddenReplies(Long noteId, Long viewerId, int limit);

  void applyReplyPolicy(Long rootId, NoteReplyPolicy policy);

  List<SelfReply> selfReplies(Collection<Long> rootIds, int depth);

  Map<Long, NoteStats> stats(Collection<Long> noteIds);

  NoteViewerMarks viewerMarks(Long userId, Collection<Long> noteIds);

  List<NoteEntity> byRemoteActor(Long remoteActorId, Long viewerId, int offset, int limit);

  Optional<Long> idByUri(String uri);

  Optional<Long> insertRemote(RemoteNoteRow row);

  int countListed(Long remoteActorId, int atMost);

  Optional<NoteEntity> findRemoteForUpdate(Long remoteActorId, Long noteId);

  int deleteRemote(Long remoteActorId, String uri);

  void muteConversation(Long userId, Long conversationId, Instant at);

  void unmuteConversation(Long userId, Long conversationId);

  List<NoteEntity> quotesOf(Long noteId, Long viewerId, int offset, int limit);

  List<NoteEntity> quotesOfPost(Long postId, Long viewerId, int offset, int limit);

  long countQuotesOfPost(Long postId, Long viewerId);

  List<NoteEntity> tagged(String tag, Long viewerId, int offset, int limit);

  List<TrendingTag> trendingTags(Instant now, int days, int minAccounts, int limit);

  List<TrendingLink> trendingLinks(Instant now, int days, int minAccounts, int limit);

  List<NoteEntity> linked(String url, Long viewerId, int offset, int limit);

  List<NoteEntity> search(String match, String like, Long viewerId, int offset, int limit);

  long countPinned(Long userId);

  void recordVersion(Long noteId, NoteVersion version);

  List<NoteVersion> versions(Long noteId);

  List<Long> pinnedIds(Long userId);

  void tag(Long noteId, List<String> tags);

  void retag(Long noteId, List<String> tags);

  long countForFollowersByAuthor(Long userId);
}
