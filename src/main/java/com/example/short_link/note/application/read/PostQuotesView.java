package com.example.short_link.note.application.read;

import java.util.List;

public record PostQuotesView(List<NoteView> items, int page, boolean hasNext, long total) {}
