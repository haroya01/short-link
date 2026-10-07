package com.example.short_link.note.presentation.request;

import java.util.List;

public record NoteFilterRequest(
    String phrase, Boolean wholeWord, List<String> context, String action, Long expiresIn) {}
