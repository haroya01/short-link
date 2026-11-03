package com.example.short_link.post.application.read;

import java.util.List;

public record CommentsView(List<CommentView> items, String next, String latest, long total) {}
