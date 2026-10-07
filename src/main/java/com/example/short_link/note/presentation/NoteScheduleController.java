package com.example.short_link.note.presentation;

import com.example.short_link.note.application.write.NoteScheduleService;
import com.example.short_link.note.presentation.request.RescheduleNoteRequest;
import com.example.short_link.note.presentation.request.ScheduleNoteRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notes/scheduled")
@RequiredArgsConstructor
public class NoteScheduleController {

  private final NoteScheduleService schedules;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NoteScheduleService.View schedule(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ScheduleNoteRequest request) {
    return schedules.schedule(userId, request.note().toDraft(), request.scheduledAt());
  }

  @GetMapping
  public List<NoteScheduleService.View> list(@AuthenticationPrincipal Long userId) {
    return schedules.list(userId);
  }

  @PatchMapping("/{id}")
  public NoteScheduleService.View reschedule(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody RescheduleNoteRequest request) {
    return schedules.reschedule(userId, id, request.scheduledAt());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> cancel(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    schedules.cancel(userId, id);
    return ResponseEntity.noContent().build();
  }
}
