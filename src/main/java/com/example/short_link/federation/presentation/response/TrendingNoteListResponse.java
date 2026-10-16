package com.example.short_link.federation.presentation.response;

import com.example.short_link.federation.application.trends.TrendingNote;
import java.util.List;

public record TrendingNoteListResponse(List<TrendingNote> items) {}
