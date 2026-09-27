package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.FeedTab;
import com.example.short_link.post.domain.UserFeedPrefEntity;
import com.example.short_link.post.domain.repository.UserFeedPrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SetFeedPrefUseCase {

  private final UserFeedPrefRepository repository;

  @Transactional
  public void setDefaultTab(Long userId, String tab) {
    if (!FeedTab.isAllowed(tab)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown feed tab: " + tab);
    }
    repository
        .findByUserId(userId)
        .ifPresentOrElse(
            pref -> {
              pref.changeDefaultTab(tab);
              repository.save(pref);
            },
            () -> repository.save(new UserFeedPrefEntity(userId, tab)));
  }
}
