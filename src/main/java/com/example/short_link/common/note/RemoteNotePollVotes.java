package com.example.short_link.common.note;

// The note slice implements this so federation can count votes from other servers without
// importing that slice. A vote names the chosen option by its title, as Mastodon sends it.
public interface RemoteNotePollVotes {

  boolean recordRemoteVote(Long noteId, Long remoteActorId, String optionTitle);
}
