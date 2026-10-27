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

  // Leaves out accounts on a suspended server and accounts the viewer blocked; a note whose
  // author is left out is shown nowhere.
  Map<Long, NoteAuthor> remoteAuthors(Collection<Long> remoteActorIds, Long viewerId);

  List<Long> followingIds(Long userId);

  Set<Long> followersOf(Long userId, Collection<Long> candidates);

  // Only a follow the account accepted counts.
  boolean followsRemote(Long userId, Long remoteActorId);
}
