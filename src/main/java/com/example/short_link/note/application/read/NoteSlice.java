package com.example.short_link.note.application.read;

import java.util.List;

public record NoteSlice(List<At> items, boolean exhausted) {

  public record At(int position, NoteView note) {}
}
