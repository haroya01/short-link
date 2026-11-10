package com.example.short_link.link.presentation.request;

import com.example.short_link.link.stats.application.LinkEventsService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClickSelectionRequest(
    @NotEmpty @Size(max = LinkEventsService.MAX_LIMIT) List<@NotNull Long> clickIds) {}
