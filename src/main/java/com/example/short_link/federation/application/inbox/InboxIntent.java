package com.example.short_link.federation.application.inbox;

import com.example.short_link.common.note.NoteSnapshotReader.NoteSnapshot;
import com.example.short_link.common.note.RemoteNoteReactions.Kind;
import com.example.short_link.federation.application.LocalActor;
import tools.jackson.databind.JsonNode;

sealed interface InboxIntent {

  default boolean cachedKeyOnly() {
    return false;
  }

  record Follow(LocalActor target) implements InboxIntent {}

  record Unfollow(String targetPublicId, String followId) implements InboxIntent {}

  record React(NoteSnapshot note, Kind kind) implements InboxIntent {}

  record Unreact(Long noteId, Kind kind, String activityId) implements InboxIntent {}

  record UndoById(String activityId) implements InboxIntent {}

  record Vote(NoteSnapshot poll, String option) implements InboxIntent {}

  record Forget() implements InboxIntent {
    @Override
    public boolean cachedKeyOnly() {
      return true;
    }
  }

  record Answer(String followId, String localActor, boolean accepted) implements InboxIntent {}

  record Receive(JsonNode object, JsonNode activity, NoteSnapshot parent) implements InboxIntent {}

  record Revise(JsonNode object, JsonNode activity, Long noteId) implements InboxIntent {}

  record Retract(String uri) implements InboxIntent {}

  record Bury(String uri) implements InboxIntent {
    @Override
    public boolean cachedKeyOnly() {
      return true;
    }
  }

  record Relayed(String objectUri) implements InboxIntent {}
}
