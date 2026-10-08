package com.example.short_link.note.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record QuotedPost(
    Long id, String title, String slug, String authorUsername, @JsonIgnore Long authorId) {}
