package com.example.short_link.profile.presentation.email;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailLeadSubmitRequest(
    @NotNull Long blockId, @NotNull @Size(max = 254) String email) {}
