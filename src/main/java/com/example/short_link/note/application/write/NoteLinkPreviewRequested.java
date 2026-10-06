package com.example.short_link.note.application.write;

// url null = the note no longer has an address to preview (edited away).
public record NoteLinkPreviewRequested(Long noteId, String url) {}
