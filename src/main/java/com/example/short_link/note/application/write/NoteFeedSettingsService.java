package com.example.short_link.note.application.write;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.repository.NoteFeedSettingsRepository;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.exception.NoteErrorCode;
import com.example.short_link.note.exception.NoteException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteFeedSettingsService {

  private final NoteFeedSettingsRepository settings;
  private final NotePeopleReader people;

  private static final int MAX_LANGUAGES = 20;

  @Transactional(readOnly = true)
  public FeedPreferences preferences(Long userId) {
    NoteFeedSettingsRepository.Preferences read = settings.read(userId);
    return new FeedPreferences(read.showReposts(), read.languages());
  }

  @Transactional
  public FeedPreferences update(Long userId, Boolean showReposts, List<String> languages) {
    if (showReposts != null) {
      settings.setShowsReposts(userId, showReposts);
    }
    if (languages != null) {
      Set<String> codes = new LinkedHashSet<>();
      for (String raw : languages) {
        String code = NoteCommandService.language(raw);
        if (code != null) {
          codes.add(code);
        }
      }
      if (codes.size() > MAX_LANGUAGES) {
        throw new NoteException(NoteErrorCode.NOTE_LANGUAGE_INVALID, codes.size());
      }
      settings.setLanguages(userId, List.copyOf(codes));
    }
    return preferences(userId);
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

  public record FeedPreferences(boolean showReposts, List<String> languages) {}

  public record RepostVisibility(boolean hidden) {}
}
