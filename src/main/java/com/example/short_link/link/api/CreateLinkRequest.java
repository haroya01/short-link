package com.example.short_link.link.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateLinkRequest(@NotBlank @URL @Size(max = 2048) String url) {}
