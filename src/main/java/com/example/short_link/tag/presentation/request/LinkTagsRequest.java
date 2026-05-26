package com.example.short_link.tag.presentation.request;

import com.example.short_link.tag.application.helper.TagSanitizer;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LinkTagsRequest(@Size(max = TagSanitizer.MAX_TAGS_PER_LINK) List<String> tags) {}
