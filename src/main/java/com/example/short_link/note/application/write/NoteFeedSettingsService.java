package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.repository.NoteFeedSettingsRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteFeedSettingsService {

  private final NoteFeedSettingsRepository settings;
  private final NotePeopleReader people;

  @Transactional(readOnly = true)
  public FeedPreferences preferences(Long userId) {
    return new FeedPreferences(settings.showsReposts(userId));
  }

  @Transactional
  public FeedPreferences setShowReposts(Long userId, boolean show) {
    settings.setShowsReposts(userId, show);
    return new FeedPreferences(show);
  }

  @Transactional(readOnly = true)
  public RepostVisibility repostsOf(Long userId, String username) {
    return new RepostVisibility(settings.hidesRepostsOf(userId, author(username).id()));
  }

  @Transactional
  public RepostVisibility setRepostsHidden(Long userId, String username, boolean hidden) {
    Long other = author(username).id();
    if (hidden) {
      settings.hideRepostsOf(userId, other);
    } else {
      settings.showRepostsOf(userId, other);
    }
    return new RepostVisibility(hidden);
  }

  private NoteAuthor author(String username) {
    return people
        .activeByUsername(username)
        .orElseThrow(() -> new NoteException(NoteErrorCode.NOTE_NOT_FOUND, username));
  }

  public record FeedPreferences(boolean showReposts) {}

  public record RepostVisibility(boolean hidden) {}
}
