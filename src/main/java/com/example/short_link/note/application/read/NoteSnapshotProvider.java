package com.example.short_link.note.application.read;

import com.example.short_link.common.note.NoteSnapshotReader;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NotePollTally;
import com.example.short_link.note.domain.QuotedPost;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NotePollRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.note.domain.repository.QuotedPostReader;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class NoteSnapshotProvider implements NoteSnapshotReader {

  private final NoteRepository notes;
  private final NoteMediaRepository media;
  private final NotePeopleReader people;
  private final QuotedPostReader quotedPosts;
  private final NotePollRepository polls;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public Optional<NoteSnapshot> find(Long noteId) {
    Optional<NoteEntity> found = notes.findById(noteId).filter(note -> !note.isRemote());
    if (found.isEmpty()) {
      return Optional.empty();
    }
    NoteEntity note = found.get();
    NoteAuthor author = people.activeAuthors(Set.of(note.getUserId())).get(note.getUserId());
    if (author == null) {
      return Optional.empty();
    }
    Quote quote = null;
    if (note.getQuotedPostId() != null) {
      QuotedPost post =
          quotedPosts.publishedByIds(Set.of(note.getQuotedPostId())).get(note.getQuotedPostId());
      if (post != null) {
        quote = new Quote(post.title(), post.slug(), post.authorUsername());
      }
    }
    QuotedNote quotedNote = null;
    if (note.getQuotedNoteId() != null) {
      Optional<NoteEntity> quoted =
          notes.findById(note.getQuotedNoteId()).filter(candidate -> !candidate.isRemote());
      if (quoted.isPresent()) {
        NoteAuthor quotedAuthor =
            people.activeAuthors(Set.of(quoted.get().getUserId())).get(quoted.get().getUserId());
        if (quotedAuthor != null) {
          quotedNote = new QuotedNote(quoted.get().getId(), quotedAuthor.username());
        }
      }
    }
    List<Image> images =
        media.findByNoteIds(List.of(noteId)).stream()
            .map(image -> new Image(image.getUrl(), image.getContentType(), image.getAltText()))
            .toList();
    return Optional.of(
        new NoteSnapshot(
            note.getId(),
            note.getUserId(),
            author.username(),
            note.getBody(),
            note.getCreatedAt(),
            note.getEditedAt(),
            note.getInReplyToId(),
            quote,
            images,
            quotedNote,
            note.getContentWarning(),
            note.isSensitive(),
            NoteSnapshotReader.Visibility.valueOf(note.getVisibility().name()),
            poll(note),
            note.getLanguage()));
  }

  private Poll poll(NoteEntity note) {
    if (!note.hasPoll()) {
      return null;
    }
    NotePollTally tally =
        polls.tallies(List.of(note.getId()), null).getOrDefault(note.getId(), NotePollTally.NONE);
    List<String> titles = note.pollOptions();
    List<PollOption> options = new ArrayList<>(titles.size());
    for (int i = 0; i < titles.size(); i++) {
      options.add(new PollOption(titles.get(i), tally.votesFor(i)));
    }
    return new Poll(
        options,
        note.getPollExpiresAt(),
        note.isPollMultiple(),
        tally.voters(),
        note.pollEndedBy(clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  public long countByAuthor(Long authorId) {
    return notes.countByAuthor(authorId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> pinnedIds(Long authorId) {
    return notes.pinnedIds(authorId);
  }
}
