package com.example.short_link.profile.presentation.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MyProfileUpdateRequest(
    @Size(max = 32) String username,
    @Size(max = 280) String bio,
    @Pattern(regexp = "^(light|dark|accent|sunset|ocean|forest|mono|neon|aurora|wave|ember)?$")
        String theme,
    @Size(max = 1024) String socials,
    Boolean hideFollowerCount) {}
