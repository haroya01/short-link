package com.example.short_link.link.presentation.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagRequest(
    @Size(max = 50) String name, @Pattern(regexp = "^(#[0-9a-fA-F]{6})?$") String color) {}
