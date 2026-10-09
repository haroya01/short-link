package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteSeries;
import java.util.Optional;

public interface NoteSeriesReader {

  Optional<NoteSeries> containing(Long noteId);
}
