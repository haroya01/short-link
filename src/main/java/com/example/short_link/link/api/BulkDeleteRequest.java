package com.example.short_link.link.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkDeleteRequest(@NotEmpty @Size(max = 100) List<String> shortCodes) {}
