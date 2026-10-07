package com.example.short_link.note.presentation;

import com.example.short_link.note.application.write.NoteFilterService;
import com.example.short_link.note.application.write.NoteFilterService.FilterView;
import com.example.short_link.note.presentation.request.NoteFilterRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteFilterController {

  private final NoteFilterService filters;

  @GetMapping("/api/v1/notes/filters")
  public List<FilterView> mine(@AuthenticationPrincipal Long userId) {
    return filters.mine(userId);
  }

  @PostMapping("/api/v1/notes/filters")
  @ResponseStatus(HttpStatus.CREATED)
  public FilterView create(
      @AuthenticationPrincipal Long userId, @RequestBody NoteFilterRequest request) {
    return filters.create(userId, draft(request));
  }

  @PutMapping("/api/v1/notes/filters/{id}")
  public FilterView update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody NoteFilterRequest request) {
    return filters.update(userId, id, draft(request));
  }

  @DeleteMapping("/api/v1/notes/filters/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    filters.delete(userId, id);
  }

  private static NoteFilterService.Draft draft(NoteFilterRequest request) {
    return new NoteFilterService.Draft(
        request.phrase(),
        request.wholeWord(),
        request.context(),
        request.action(),
        request.expiresIn());
  }
}
