package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.MentionCandidateService;
import com.example.short_link.user.domain.repository.MentionCandidateReader;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MentionCandidateController {

  private final MentionCandidateService candidates;

  @GetMapping("/api/v1/users/me/mention-candidates")
  public List<MentionCandidateReader.Candidate> candidates(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "8") int limit) {
    return candidates.candidates(userId, q, limit);
  }
}
