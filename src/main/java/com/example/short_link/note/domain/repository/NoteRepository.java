package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteFeedRow;
import com.example.short_link.note.domain.NoteStats;
import com.example.short_link.note.domain.NoteVersion;
import com.example.short_link.note.domain.NoteViewerMarks;
import com.example.short_link.note.domain.RemoteNoteRow;
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

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  List<NoteEntity> topLevel(Long viewerId, int offset, int limit);

  List<NoteEntity> topLevelByAuthor(Long authorId, Long viewerId, int offset, int limit);

  Set<Long> visibleTo(Long viewerId, Collection<Long> restrictedIds);

  void addRecipients(Long noteId, Collection<Long> userIds);

  List<NoteEntity> direct(Long viewerId, int offset, int limit);

  List<NoteEntity> trending(Long viewerId, int offset, int limit);

  List<NoteEntity> federated(Long viewerId, int offset, int limit);

  List<NoteFeedRow> following(Collection<Long> authorIds, Long viewerId, int offset, int limit);

  List<NoteEntity> replies(Long noteId, Long viewerId, int limit);

  List<SelfReply> selfReplies(Collection<Long> rootIds, int depth);

  Map<Long, NoteStats> stats(Collection<Long> noteIds);

  NoteViewerMarks viewerMarks(Long userId, Collection<Long> noteIds);

  List<NoteEntity> byRemoteActor(Long remoteActorId, Long viewerId, int offset, int limit);

  Optional<Long> idByUri(String uri);

  Optional<Long> insertRemote(RemoteNoteRow row);

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

  long countByAuthor(Long userId);
}
