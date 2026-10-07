package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.FollowSuggestionService;
import com.example.short_link.user.domain.repository.FollowSuggestionReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/suggestions")
@RequiredArgsConstructor
public class FollowSuggestionController {

  private final FollowSuggestionService suggestions;

  @GetMapping
  public List<FollowSuggestionReader.Suggestion> suggestions(
      @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = "10") int limit) {
    return suggestions.suggestions(userId, limit);
  }

  @DeleteMapping("/{username}")
  public ResponseEntity<Void> dismiss(
      @AuthenticationPrincipal Long userId, @PathVariable String username) {
    suggestions.dismiss(userId, username);
    return ResponseEntity.noContent().build();
  }
}
