package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface NotePollRepository {

  Map<Long, NotePollTally> tallies(Collection<Long> noteIds, Long viewerId);

  boolean vote(Long noteId, Long userId, int choices);

  boolean voteRemote(Long noteId, Long remoteActorId, int choices, boolean multiple);

  Map<Long, List<Long>> voterIds(Collection<Long> noteIds);

  List<NoteEntity> due(Instant now, int limit);

  void close(Collection<Long> noteIds, Instant at);
}
