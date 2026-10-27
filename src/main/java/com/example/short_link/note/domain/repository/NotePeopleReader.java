package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteAuthor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Soft-deleted accounts never resolve.
public interface NotePeopleReader {

  Map<Long, NoteAuthor> activeAuthors(Collection<Long> userIds);

  Map<Long, NoteAuthor> activeAuthors(Collection<Long> userIds, Collection<String> usernames);

  Optional<NoteAuthor> activeByUsername(String username);

  Map<Long, NoteAuthor> remoteAuthors(Collection<Long> remoteActorIds, Long viewerId);

  List<Long> followingIds(Long userId);

  Set<Long> followersOf(Long userId, Collection<Long> candidates);

  boolean followsRemote(Long userId, Long remoteActorId);
}
