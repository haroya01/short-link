package com.example.short_link.common.federation;

// The federation slice implements this so the note slice can fill in an account's earlier notes
// before it lists them, without importing that slice.
public interface RemoteHistory {

  void fillIn(Long remoteActorId, Long viewerId);
}
