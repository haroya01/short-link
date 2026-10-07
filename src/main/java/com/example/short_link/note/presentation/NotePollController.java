package com.example.short_link.note.presentation;

import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.write.NotePollService;
import com.example.short_link.note.presentation.request.NotePollVoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotePollController {

  private final NotePollService polls;

  @PostMapping("/api/v1/notes/{id}/poll/votes")
  public NoteView.Poll vote(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody NotePollVoteRequest request) {
    return polls.vote(userId, id, request.choices());
  }
}
