package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteAuthor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Reads the user slice's tables natively; soft-deleted accounts never resolve, so their notes drop
// out of every listing at once.
public interface NotePeopleReader {

  Map<Long, NoteAuthor> activeAuthors(Collection<Long> userIds);

  Map<Long, NoteAuthor> activeAuthors(Collection<Long> userIds, Collection<String> usernames);

  Optional<NoteAuthor> activeByUsername(String username);

  Map<Long, NoteAuthor> remoteAuthors(Collection<Long> remoteActorIds);

  List<Long> followingIds(Long userId);

  // Of the candidates, those who follow the user.
  Set<Long> followersOf(Long userId, Collection<Long> candidates);

  // Whether the member follows the account elsewhere and it accepted.
  boolean followsRemote(Long userId, Long remoteActorId);
}
