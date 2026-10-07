package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class NotePolls {

  private NotePolls() {}

  public static NoteView.Poll view(
      NoteEntity note, NotePollTally tally, Long viewerId, Instant now) {
    List<String> titles = note.pollOptions();
    List<NoteView.PollOption> options = new ArrayList<>(titles.size());
    long votes = 0;
    for (int i = 0; i < titles.size(); i++) {
      long count = tally.votesFor(i);
      votes += count;
      options.add(new NoteView.PollOption(titles.get(i), count));
    }
    return new NoteView.Poll(
        note.getPollExpiresAt(),
        note.pollEndedBy(now),
        note.isPollMultiple(),
        votes,
        tally.voters(),
        options,
        viewerId == null ? null : note.isOwnedBy(viewerId) || tally.viewerChoices() != null,
        viewerId == null ? null : indexes(tally.viewerChoices()));
  }

  public static List<Integer> indexes(Integer choices) {
    List<Integer> indexes = new ArrayList<>();
    if (choices == null) {
      return indexes;
    }
    for (int i = 0; i < NoteEntity.MAX_POLL_OPTIONS; i++) {
      if ((choices & (1 << i)) != 0) {
        indexes.add(i);
      }
    }
    return indexes;
  }
}
