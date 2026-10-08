package com.example.short_link.note.presentation.request;

import com.example.short_link.note.application.write.NoteCommandService;
import com.example.short_link.note.application.write.NoteDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateNoteThreadRequest(
    @NotNull @Size(min = 2, max = NoteCommandService.MAX_THREAD_NOTES)
        List<@Valid @NotNull CreateNoteRequest> notes) {

  public List<NoteDraft> toDrafts() {
    return notes.stream().map(CreateNoteRequest::toDraft).toList();
  }
}
