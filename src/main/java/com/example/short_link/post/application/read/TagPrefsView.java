package com.example.short_link.post.application.read;

import java.util.List;

public record TagPrefsView(List<String> followed, List<String> hidden) {}
