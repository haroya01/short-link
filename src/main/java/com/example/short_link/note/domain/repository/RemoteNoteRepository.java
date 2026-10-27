package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.RemoteNoteRow;
import java.util.Optional;

public interface RemoteNoteRepository {

  Optional<Long> idByUri(String uri);

  Optional<Long> insertRemote(RemoteNoteRow row);

  int countListed(Long remoteActorId, int atMost);

  Optional<NoteEntity> findRemoteForUpdate(Long remoteActorId, Long noteId);

  int deleteRemote(Long remoteActorId, String uri);
}
