package com.example.short_link.post.domain;

/** A tag and how many published posts carry it — drives the 주제 (tag) exploration page. */
public record TagCount(String tag, long count) {}
