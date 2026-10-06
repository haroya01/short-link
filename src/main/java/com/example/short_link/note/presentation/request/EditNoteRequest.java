package com.example.short_link.note.presentation.request;

// contentWarning and sensitive are left as they are when absent; an empty warning removes it.
public record EditNoteRequest(String body, String contentWarning, Boolean sensitive) {}
