package com.example.short_link.link.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateLinkRequest(
    @NotBlank
        @URL
        @Pattern(regexp = "^https?://.*", message = "URL must use http or https")
        @Size(max = 2048)
        String url) {}
