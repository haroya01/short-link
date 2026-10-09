package com.example.short_link.note.domain;

// One of an author's own replies under a root note: the parts of a thread written in turns.
public record SelfReply(Long rootId, Long parentId, Long id) {}
