package com.example.short_link.profile.presentation.request;

import com.example.short_link.profile.application.BlockContentValidator;
import jakarta.validation.constraints.Size;

public record ProfileBlockUpdateRequest(
    @Size(max = BlockContentValidator.MAX_CONTENT) String content) {}
