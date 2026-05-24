package com.example.short_link.link.presentation.response;

import java.util.List;

public record LinkTagsResponse(String shortCode, List<String> tags) {}
