package com.example.short_link.note.domain;

import java.util.List;

// votes holds one count per option index. viewerChoices is the viewer's bitmask, or null when they
// have not voted (or nobody is signed in).
public record NotePollTally(long voters, List<Long> votes, Integer viewerChoices) {

  public static final NotePollTally NONE = new NotePollTally(0, List.of(), null);

  public long votesFor(int option) {
    return option < votes.size() ? votes.get(option) : 0;
  }
}
