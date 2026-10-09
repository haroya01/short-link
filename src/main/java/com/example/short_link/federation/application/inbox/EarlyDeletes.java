package com.example.short_link.federation.application.inbox;

// Mastodon's delete_upon_arrival: a Delete that reaches this server before the note it deletes is
// held for a while, so the note's late Create is dropped instead of kept for good.
public interface EarlyDeletes {

  void remember(Long remoteActorId, String uri);

  boolean remembered(Long remoteActorId, String uri);
}
