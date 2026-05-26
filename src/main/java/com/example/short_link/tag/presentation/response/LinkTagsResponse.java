package com.example.short_link.tag.presentation.response;

import com.example.short_link.link.domain.ShortCode;
import java.util.List;

public record LinkTagsResponse(ShortCode shortCode, List<String> tags) {}
