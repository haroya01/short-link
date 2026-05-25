package com.example.short_link.tag.presentation.response;

import java.util.List;

public record LinkTagsResponse(String shortCode, List<String> tags) {}
