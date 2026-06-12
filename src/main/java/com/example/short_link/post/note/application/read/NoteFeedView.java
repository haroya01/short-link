package com.example.short_link.post.note.application.read;

import com.example.short_link.post.note.domain.NoteRow;
import java.util.List;

public record NoteFeedView(List<NoteRow> items, int page, boolean hasNext) {}
