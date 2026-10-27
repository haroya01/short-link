package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteReplyPolicy;
import com.example.short_link.note.domain.NoteVersion;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  Optional<NoteEntity> findUnblocked(Long id, Long viewerId);

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  void addRecipients(Long noteId, Collection<Long> userIds);

  void applyReplyPolicy(Long rootId, NoteReplyPolicy policy);

  void muteConversation(Long userId, Long conversationId, Instant at);

  void unmuteConversation(Long userId, Long conversationId);

  long countPinned(Long userId);

  void recordVersion(Long noteId, NoteVersion version);

  List<NoteVersion> versions(Long noteId);

  List<Long> pinnedIds(Long userId);

  void tag(Long noteId, List<String> tags);

  void retag(Long noteId, List<String> tags);

  long countForFollowersByAuthor(Long userId);
}
