package com.example.short_link.note.application.read;

import java.util.List;

public record NoteThreadView(NoteView note, NoteView parent, List<NoteView> replies) {}
