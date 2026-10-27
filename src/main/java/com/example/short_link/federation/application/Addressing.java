package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.RemoteActorEntity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Who on other servers a note addresses: the note it answers, the people it names and the note it
// quotes.
public record Addressing(
    RemoteParents.Parent parent, List<RemoteActorEntity> named, RemoteParents.Parent quoted) {

  public boolean isEmpty() {
    return parent == null && named.isEmpty() && quoted == null;
  }

  public List<NoteDocuments.Addressee> addressees() {
    return named.stream()
        .map(
            person ->
                new NoteDocuments.Addressee(
                    person.getActorUri(),
                    "@" + person.getUsername() + "@" + person.getDomain(),
                    person.getProfileUrl() == null ? person.getActorUri() : person.getProfileUrl()))
        .toList();
  }

  public List<String> inboxes(List<String> followerInboxes) {
    Set<String> inboxes = new LinkedHashSet<>(followerInboxes);
    if (parent != null) {
      inboxes.add(parent.inbox());
    }
    if (quoted != null) {
      inboxes.add(quoted.inbox());
    }
    named.forEach(person -> inboxes.add(person.deliveryInbox()));
    return List.copyOf(inboxes);
  }
}
