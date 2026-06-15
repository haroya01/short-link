package com.example.short_link.post.collection.presentation.request;

import com.example.short_link.post.collection.domain.ConnectionBlockType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConnectBlockRequest(
    @NotNull ConnectionBlockType blockType, @NotNull Long refId, @Size(max = 280) String why) {}
