package com.example.short_link.post.application.read;

/** A discovery-rail author suggestion — public author info plus their published-post count. */
public record SuggestedAuthorView(PublicAuthorView author, long postCount) {}
