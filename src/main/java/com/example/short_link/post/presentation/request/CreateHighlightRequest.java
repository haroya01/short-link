package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateHighlightRequest(
    @NotNull @Min(0) Integer blockOrder,
    @Min(0) Integer endBlockOrder,
    @NotNull @Min(0) Integer startOffset,
    @NotNull @Min(0) Integer endOffset,
    @NotBlank String quote,
    @Size(max = 500) String note) {}
