package com.example.short_link.note.presentation.request;

import java.util.List;

public record NoteFeedPreferencesRequest(Boolean showReposts, List<String> languages) {}
