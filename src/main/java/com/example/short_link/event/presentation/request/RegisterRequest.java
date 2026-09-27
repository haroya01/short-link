package com.example.short_link.event.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record RegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 254) String contact,
    Map<Long, String> answers) {}
